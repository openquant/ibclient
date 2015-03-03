package com.larroy.ibclient.util

/**
 * @author piotr 03.03.15
 */

import com.ib.client.Types.{DurationUnit, BarSize}
import org.joda.time.format.DateTimeFormat
import org.specs2.mutable._

class HistoryLimitsSpec extends Specification {
  "HistoryLimitsSpec" should {
    "return propper HistoryDuration" in {
      val fmt = DateTimeFormat.forPattern("yyyyMMdd kk:mm:ss")
      def toDate(s: String) = fmt.parseDateTime(s).toDate
      HistoryLimits.bestDuration(toDate("20150303 10:00:00"), toDate("20150303 10:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(1,60, DurationUnit.DAY)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(1,60, DurationUnit.DAY)
      HistoryLimits.bestDuration(toDate("20150301 09:00:00"), toDate("20150303 09:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(2,60, DurationUnit.DAY)
    }
  }
}