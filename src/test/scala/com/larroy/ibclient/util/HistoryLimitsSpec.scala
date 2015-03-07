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
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._5_secs) shouldEqual new HistoryDuration(3600, 10000, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._30_secs) shouldEqual new HistoryDuration(3600, 10000, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._1_min) shouldEqual new HistoryDuration(3600, 86400, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._5_mins) shouldEqual new HistoryDuration(3600, 86400, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._15_mins) shouldEqual new HistoryDuration(3600, 86400, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._30_mins) shouldEqual new HistoryDuration(3600, 86400, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._1_hour) shouldEqual new HistoryDuration(3600, 86400, DurationUnit.SECOND)
      HistoryLimits.bestDuration(toDate("20150303 10:00:00"), toDate("20150303 10:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(1,60, DurationUnit.DAY)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(1,60, DurationUnit.DAY)
      HistoryLimits.bestDuration(toDate("20150301 09:00:00"), toDate("20150303 09:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(2,60, DurationUnit.DAY)
      HistoryLimits.bestDuration(toDate("20150203 09:00:00"), toDate("20150303 09:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(4, 52, DurationUnit.WEEK)
      HistoryLimits.bestDuration(toDate("20150201 09:00:00"), toDate("20150303 09:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(1, 12, DurationUnit.MONTH)
      HistoryLimits.bestDuration(toDate("20150131 09:00:00"), toDate("20150303 09:00:00"), BarSize._1_day) shouldEqual new HistoryDuration(2, 12, DurationUnit.MONTH)
    }
  }
}