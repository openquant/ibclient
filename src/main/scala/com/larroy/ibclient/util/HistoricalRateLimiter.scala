package com.larroy.ibclient.util

import java.util.Calendar

import com.google.common.collect.TreeMultimap
import scala.collection.JavaConversions._
import org.slf4j.{Logger, LoggerFactory}


/**
 * Stateful utility to account for historical requests in order no to violate historical data limitations as specified in:
 * https://www.interactivebrokers.com/en/software/api/apiguide/tables/historical_data_limitations.htm
 *
 * The basic usage is done through the following calls:
 *
 * Requests should be accounted for with the "requested" method
 * nextRequestAfter_ms gives us the wait time until the next request can be made
 * cleanup will free the accounting of old requests, should be called periodically
 *
 * @author piotr 01.03.15
 * Limitations:
 *         1. Making identical historical data requests within 15 seconds;
 *         2. Making six or more historical data requests for the same Contract, Exchange and Tick Type within two seconds.
 *         3. Do not make more than 60 historical data requests in any ten-minute period.
 */
class HistoricalRateLimiter {
  private[this] val log: Logger = LoggerFactory.getLogger(this.getClass)
  // latest times are first
  // map time of request in millis to request
  private[this] val requests = TreeMultimap.create[Long, HistoricalRequest](
    implicitly[Ordering[Long]].reverse,
    implicitly[Ordering[HistoricalRequest]]
  )

  def now_ms: Long = Calendar.getInstance().getTimeInMillis()

  /**
   * Account for an historical request in this rate limiter
   * @param request
   * @param reftime_ms
   */
  def requested(request: HistoricalRequest, reftime_ms: Option[Long] = None): Unit = synchronized {
    var curTime = reftime_ms.getOrElse(now_ms)
    while (requests.put(curTime, request) == false) {
      log.warn(s"Incrememtomg reftime of request ${request}, another request with the same time ${curTime}")
      curTime += 1
    }
  }

  protected def latestInLast(timeframe_ms: Long, reftime_ms: Long = now_ms): Iterator[java.util.Map.Entry[Long, HistoricalRequest]] = {
    requests.entries.iterator.takeWhile { x ⇒ x.getKey > reftime_ms - timeframe_ms}
  }

  /**
   * @param timeframe_ms timeframe to consider
   * @param numRequests the allowed number of requests in this timeframe
   * @param filter a comparison functor to filter only the given requests if nonEmpty
   * @param reftime_ms the reference time (now)
   * @return number of ms after we are able to have numRequests requests or less in the given timeframe with the optional filter
   */
  def nextSlot_ms(timeframe_ms: Long, numRequests: Int, filter: Option[(HistoricalRequest) ⇒ Boolean] = None, reftime_ms: Long = now_ms): Long = {
    val latest = if (filter.isEmpty)
      latestInLast(timeframe_ms, reftime_ms).toVector
    else
      latestInLast(timeframe_ms, reftime_ms).toVector.filter(x ⇒ filter.get(x.getValue))

    if (latest.size >= numRequests) {
      val nextSlotIn_ms = timeframe_ms - (reftime_ms - latest.take(numRequests).last.getKey)
      if (nextSlotIn_ms > 0)
        return nextSlotIn_ms
    }
    0L
  }

  /**
   * @param request type of request that we want to make
   * @param reftime_ms the reference time, what is considered "now", defaults to the current time
   * @return minimum milliseconds to wait after we can make the next request without violating the limits
   */
  def nextRequestAfter_ms(request: HistoricalRequest, reftime_ms: Long = now_ms): Long = synchronized {
    var after_ms = 0L
    // Rate limit on restriction 1
    // this is conservative as some arguments might make a different request, but it's tricky (BID_ASK for example counts as two)
    val identical = { req: HistoricalRequest ⇒
      req == request
    }
    after_ms = Math.max(after_ms, nextSlot_ms(15L * 1000, 1, Some(identical), reftime_ms))
    log.debug(s"delayed ${after_ms} (identical requests within 15 s)")

    // restriction 2
    val sameContract = { req: HistoricalRequest ⇒
      (req.contract, req.exchange, req.barSize) == (request.contract, request.exchange, req.barSize)
    }
    after_ms = Math.max(after_ms, nextSlot_ms(2L * 1000, 5, Some(sameContract), reftime_ms))
    log.debug(s"delayed ${after_ms} 6 or more same contract within 2 s")

    // restriction 3
    after_ms = Math.max(after_ms, nextSlot_ms(10L * 60 * 1000, 60, None, reftime_ms))
    log.debug(s"delayed ${after_ms} no more than 60 in 10 min")
    after_ms
  }

  def cleanupAfter(time_ms: Long): Unit = synchronized {
    val expired = requests.keys.filter { x ⇒ x < time_ms }
    expired.foreach { key ⇒
      requests.removeAll(key)
    }
  }

  def cleanup(reftime_ms: Long = now_ms): Unit = {
    cleanupAfter(reftime_ms + 10L * 60 * 1000)
  }
}
