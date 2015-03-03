package com.larroy.ibclient.util

import org.specs2.mutable._

class UtilSpec extends Specification {
  "dateEpoch_s" should {
    "return seconds since the epoch" in {
      dateEpoch_s("20150303") shouldEqual(1425337200)
      dateEpoch_s("1425337200") shouldEqual(1425337200)
    }
  }
}