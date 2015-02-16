package com.larroy.trabot.ib

import com.ib.client.Contract
import rx.lang.scala.Observable

/**
 * @author piotr 16.02.15
 */
case class MarketDataSubscription(ibclient: IBClient, id: Int, contract: Contract, data: Observable[Tick]) {
  def close(): Unit = {
    ibclient.closeMarketData(id)
  }
}
