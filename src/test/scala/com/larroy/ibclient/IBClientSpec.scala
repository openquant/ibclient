package com.larroy.ibclient

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}

import com.ib.client.Types.{BarSize, DurationUnit, WhatToShow, SecType}
import com.larroy.ibclient.contract.{CashContract, StockContract}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * @author piotr 11.02.15
 */

import org.specs2.mutable._

class IBClientSpec extends Specification {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  val cfg = ConfigFactory.load().getConfig("ibclient.test")
  var (host, port, clientId) = (cfg.getString("tws.host"), cfg.getInt("tws.port"), cfg.getInt("tws.clientId"))

  val ibclient: IBClient = {
    new IBClient(host, port, clientId).connectBlocking(cfg.getInt("tws.timeout_s"))
  }

  def testWaitDuration = Duration(cfg.getInt("tws.timeout_s"), SECONDS)

  def testStockContract: StockContract = {
    new StockContract(
      cfg.getString("params.stock.contract"),
      cfg.getString("params.stock.exchange"),
      cfg.getString("params.stock.currency")
    )
  }

  "IBClientSpec" should {
    "contract details" in {
      val stockContract = testStockContract
      val futureContractDetails = ibclient.contractDetails(stockContract)
      val contractDetails = Await.result(futureContractDetails, testWaitDuration)
      contractDetails must not be empty
    }

    "historical data" in {
      val stockContract = testStockContract
      val res = ibclient.historicalData(stockContract, new Date(), 10,
        DurationUnit.DAY, BarSize._1_hour, WhatToShow.MIDPOINT, false
      )
      val hist = Await.result(res, testWaitDuration)
      hist must not be empty
    }

    "easy historical data" in {
      import org.joda.time._
      val stockContract = testStockContract
      val startDate = new DateTime(2015, 3, 1, 15, 0).toDate
      val endDate = new DateTime(2015, 3, 3, 15, 0).toDate
      val res = ibclient.easyHistoricalData(stockContract, startDate, endDate, BarSize._1_min, WhatToShow.TRADES)
      val hist = Await.result(res, testWaitDuration)
      hist must not be empty
    }

    "get easy historical data in order" in {
      import org.joda.time._
      val stockContract = testStockContract
      val startDate = new DateTime(2008, 3, 3, 15, 0).toDate
      val endDate = new DateTime(2015, 12, 31, 15, 0).toDate
      val res = ibclient.easyHistoricalData(stockContract, startDate, endDate, BarSize._1_day, WhatToShow.TRADES)
      val hist = Await.result(res, testWaitDuration)
      hist must not be empty
      hist mustEqual hist.sorted(Ordering[Bar].reverse)
    }

    "market data" in {
      val result = ArrayBuffer.empty[Tick]
      val subscription = ibclient.marketData(new CashContract("EUR", "EUR.USD"))
      val currThread = Thread.currentThread()
      subscription.observableTick.subscribe(
        { tick ⇒
          log.debug(s"Got tick ${tick}")
          result += tick
          if (result.length >= 3) {
            log.debug(s"Closing subscription: ${subscription.id}")
            subscription.close()
          }
        },
        {error ⇒ throw (error)},
        {() ⇒
          println("Closed")
          currThread.interrupt()
        }
      )
      try {
        Thread.sleep(testWaitDuration.toMillis)
        log.error("Timeout waiting for market data")
      } catch {
        case e: InterruptedException ⇒
      }
      ((result.length >= 1)  must beTrue).setMessage("We didn't receive market data")
    }

    "positions" in {
      val pos = Await.result(ibclient.positions(), testWaitDuration)
      pos must not be empty
    }
    "realtime bars" in {
      val subscription = ibclient.realtimeBars(new CashContract("EUR", "EUR.USD"))
      // The functions passed to subscribe are executed in the EReader thread
      // This can be changed by using observeOn
      // subscription.observableBar.observeOn(ComputationScheduler()).subscribe({bar=>log.debug(s"got bar ${bar}")},{error ⇒ throw (error)})
      val bars = new ArrayBlockingQueue[Bar](64)
      subscription.observableBar.subscribe(
        { bar =>
          log.debug(s"got bar ${bar}")
          val succ = bars.offer(bar)
          if (! succ)
            log.debug("Bar queue full")
        },
        { error ⇒
          throw error
        }
      )
      val bar = Option(bars.poll(3, TimeUnit.SECONDS))
      subscription.close
      if (bar.isEmpty)
        log.warn("We didn't receive market data, are we outside RTH?")
      //(bar must not be empty).setMessage("We didn't receive market data")
      success
    }
    "return a failed future when it can't connect" in {
      val futureIBclient = new IBClient("host.invalid", port, clientId).connect()
      Await.result(futureIBclient, testWaitDuration) must throwAn[Exception]
    }
  }
}
