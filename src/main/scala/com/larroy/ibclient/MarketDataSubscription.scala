package com.larroy.ibclient

import com.ib.client.Contract
import rx.lang.scala.Observable

/**
 * @author piotr 16.02.15
 */
case class MarketDataSubscription(ibclient: IBClient, id: Int, contract: Contract, observableTick: Observable[Tick]) {
  def close(): Unit = {
    ibclient.closeMarketData(id)
  }
}
