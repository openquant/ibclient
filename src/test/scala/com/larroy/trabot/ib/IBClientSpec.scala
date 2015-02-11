package com.larroy.trabot.ib


import java.text.SimpleDateFormat
import java.util.Date

import com.ib.client.Contract
import com.ib.client.Types.{BarSize, DurationUnit, WhatToShow, SecType}
import com.larroy.trabot.ib.contract.{FutureContract, StockContract}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * @author piotr 11.02.15
 */

import org.specs2.mutable._

class IBClientSpec extends Specification {
  val cfg = ConfigFactory.load().getConfig("trabot.test")
  val ibclient = connectedClient
  def testWaitDuration = Duration(cfg.getInt("tws.timeout_s"), SECONDS)
  def connectedClient: IBClient = {
    val ibclient = new IBClient(cfg.getString("tws.host"), cfg.getInt("tws.port"), cfg.getInt("tws.clientId"))
    Await.result(ibclient.connect(), testWaitDuration)
    ibclient
  }

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
      val endDate = new SimpleDateFormat("yyyyMMdd hh:mm:ss").format(new Date())
      val res = ibclient.historicalData(stockContract, endDate, 10,
        DurationUnit.DAY, BarSize._1_hour, WhatToShow.MIDPOINT, false
      )
      val hist = Await.result(res, testWaitDuration)
      hist must not be empty
    }
  }
}