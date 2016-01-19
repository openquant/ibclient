package com.larroy.ibclient.util

/**
 * @author piotr 01.03.15
 */

import java.util.Date

import com.ib.client.Types.{BarSize, DurationUnit}
import org.specs2.mutable._

class HistoricalRateLimiterSpec extends Specification {
  "HistoricalRateLimiterSpec" should {
    "limit requests" in {
      val rl = new HistoricalRateLimiter
      rl.nextSlot_ms(10, 2) mustEqual 0L
      val request = new HistoricalRequest("SPY", "GLOBEX", new Date(), 5, DurationUnit.DAY, BarSize._10_mins)

      val now = rl.now_ms
      rl.requested(request, Some(now))

      // we have one request, the next slot is after 1000 ms
      rl.nextSlot_ms(1000, 1, None, now) mustEqual 1000L
      // we can have another request right away
      rl.nextSlot_ms(1000, 2, None, now) mustEqual 0L

      rl.requested(request)
      rl.nextSlot_ms(1000, 2, None, now) must beGreaterThan(0L)
      rl.nextSlot_ms(1000, 3, None, now) mustEqual 0L
    }
    "honor ib limits" in {
      val rl = new HistoricalRateLimiter
      val date = new Date()
      val request = new HistoricalRequest("SPY", "GLOBEX", date, 5, DurationUnit.DAY, BarSize._10_mins)
      rl.nextRequestAfter_ms(request) mustEqual 0L
      rl.requested(request)
      rl.nextRequestAfter_ms(request) must beGreaterThan(0L)

      val request2 = new HistoricalRequest("CL", "NYMEX", date, 5, DurationUnit.DAY, BarSize._10_mins)
      rl.nextRequestAfter_ms(request2) mustEqual 0L
      rl.requested(request2)
      rl.nextRequestAfter_ms(request2) must beGreaterThan(0L)
      
      rl.cleanupAfter(rl.now_ms)
      rl.nextRequestAfter_ms(request2) mustEqual 0L
    }
  }
}