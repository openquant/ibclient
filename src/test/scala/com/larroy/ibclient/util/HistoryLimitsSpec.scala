package com.larroy.ibclient.util

/**
 * @author piotr 03.03.15
 */

import org.joda.time.format.DateTimeFormat
import org.specs2.mutable._

class HistoryLimitsSpec extends Specification {
  "HistoryLimitsSpec" should {
    "return propper HistoryDuration" in {
      val fmt = DateTimeFormat.forPattern("yyyyMMdd kk:mm:ss")
      def toDate(s: String) = fmt.parseDateTime(s).toDate
      import com.ib.client.Types.DurationUnit._
      import com.ib.client.Types.BarSize._
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _5_secs) shouldEqual new HistoryDuration(3600, 10000, SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _30_secs) shouldEqual new HistoryDuration(3600, 10000, SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _1_min) shouldEqual new HistoryDuration(3600, 86400, SECOND)
      HistoryLimits.bestDuration(toDate("20150101 09:00:00"), toDate("20150303 10:00:00"), _1_min) shouldEqual new  HistoryDuration(62, 10, DAY)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _5_mins) shouldEqual new HistoryDuration(3600, 86400, SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _15_mins) shouldEqual new HistoryDuration(3600, 86400, SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _30_mins) shouldEqual new HistoryDuration(3600, 86400, SECOND)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _1_hour) shouldEqual new HistoryDuration(3600, 86400, SECOND)
      HistoryLimits.bestDuration(toDate("20150303 10:00:00"), toDate("20150303 10:00:00"), _1_day) shouldEqual new HistoryDuration(1, 60, DAY)
      HistoryLimits.bestDuration(toDate("20150303 09:00:00"), toDate("20150303 10:00:00"), _1_day) shouldEqual new HistoryDuration(1, 60, DAY)
      HistoryLimits.bestDuration(toDate("20150301 09:00:00"), toDate("20150303 09:00:00"), _1_day) shouldEqual new HistoryDuration(2, 60, DAY)
      HistoryLimits.bestDuration(toDate("20150203 09:00:00"), toDate("20150303 09:00:00"), _1_day) shouldEqual new HistoryDuration(4, 52, WEEK)
      HistoryLimits.bestDuration(toDate("20150201 09:00:00"), toDate("20150303 09:00:00"), _1_day) shouldEqual new HistoryDuration(1, 12, MONTH)
      HistoryLimits.bestDuration(toDate("20150131 09:00:00"), toDate("20150303 09:00:00"), _1_day) shouldEqual new HistoryDuration(2, 12, MONTH)
    }
  }
}