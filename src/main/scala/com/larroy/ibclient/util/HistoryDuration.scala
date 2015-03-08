package com.larroy.ibclient.util

import java.util.Date

import com.ib.client.Types.DurationUnit
import org.joda.time.DateTime

/**
 * @param total
 * @param eachRequest
 * @param durationUnit
 */
case class HistoryDuration(total: Int, eachRequest: Int, durationUnit: DurationUnit) {
  /**
   * @return number of requests to perform with the available parameters
   */
  def numRequests: Int = divRoundAwayZero(total, eachRequest)

  def minusDuration(dateTime: DateTime, amt: Int, durationUnit: DurationUnit): DateTime = {
    import com.ib.client.Types.DurationUnit._
    durationUnit match {
      case SECOND ⇒ dateTime.minusSeconds(amt)
      case DAY ⇒ dateTime.minusDays(amt)
      case WEEK ⇒ dateTime.minusWeeks(amt)
      case MONTH ⇒ dateTime.minusMonths(amt)
      case YEAR ⇒ dateTime.minusYears(amt)
    }
  }

  /**
   * @param endDate the end date of the request
   * @return a vector of dates to request
   */
  def endDates(endDate: Date): Vector[Date] = {
    val endDateTime = new DateTime(endDate)
    Vector.tabulate(numRequests) { reqNum ⇒
      minusDuration(endDateTime, reqNum * eachRequest, durationUnit).toDate
    }
  }

  /**
   * @return a vector of durations to request from past to present
   */
  def durations: Vector[Int] = {
    val res = Vector.tabulate(numRequests){ reqNum ⇒
      val rem = total % eachRequest
      if (reqNum == numRequests - 1 && rem != 0)
        rem
      else
        eachRequest
    }
    res.foreach { x ⇒ assert(x != 0, "calculated duration can't be 0") }
    res
  }
}
