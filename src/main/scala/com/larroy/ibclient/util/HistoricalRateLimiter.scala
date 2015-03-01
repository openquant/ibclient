package com.larroy.ibclient.util

import java.util.Calendar

import com.google.common.collect.TreeMultimap
import scala.collection.JavaConversions._


/**
 * @author piotr 01.03.15
 *         1. Making identical historical data requests within 15 seconds;
 *         2. Making six or more historical data requests for the same Contract, Exchange and Tick Type within two seconds.
 *         3. Do not make more than 60 historical data requests in any ten-minute period.
 */
class HistoricalRateLimiter {
  // latest times are first
  // map time of request in millis to request
  private[this] val requests = TreeMultimap.create[Long, HistoricalRequest](
    implicitly[Ordering[Long]].reverse,
    implicitly[Ordering[HistoricalRequest]]
  )

  def now_ms: Long = Calendar.getInstance().getTimeInMillis()

  def requested(request: HistoricalRequest, reftime_ms: Long = now_ms): Unit = {
    requests.put(reftime_ms, request)
  }

  def latestInLast(timeframe_ms: Long, reftime_ms: Long = now_ms): Iterator[java.util.Map.Entry[Long, HistoricalRequest]] = {
    requests.entries.iterator.takeWhile { x ⇒ x.getKey > reftime_ms - timeframe_ms}
  }

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

  def nextRequestAfter_ms(request: HistoricalRequest, reftime_ms: Long = now_ms): Long = {
    var after_ms = 0L
    // Rate limit on restriction 1
    // this is conservative as some arguments might make a different request, but it's tricky (BID_ASK for example counts as two)
    val identical = { req: HistoricalRequest ⇒
      req == request
    }
    after_ms = Math.max(after_ms, nextSlot_ms(15L * 1000, 1, Some(identical), reftime_ms))

    // restriction 2
    val sameContract = { req: HistoricalRequest ⇒
      (req.contract, req.exchange, req.barSize) == (request.contract, request.exchange, req.barSize)
    }
    after_ms = Math.max(after_ms, nextSlot_ms(2L * 1000, 5, Some(sameContract), reftime_ms))

    // restriction 3
    after_ms = Math.max(after_ms, nextSlot_ms(10L * 60 * 1000, 60, None, reftime_ms))
    after_ms
  }

  def cleanupAfter(time_ms: Long): Unit = {
    val expired = requests.keys.filter { x ⇒ x < time_ms }
    expired.foreach { key ⇒
      requests.removeAll(key)
    }
  }

  def cleanup(reftime_ms: Long = now_ms): Unit = {
    cleanupAfter(reftime_ms + 10L * 60 * 1000)
  }
}
