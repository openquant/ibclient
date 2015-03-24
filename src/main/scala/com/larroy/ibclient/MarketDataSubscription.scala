package com.larroy.ibclient

import com.ib.client.Contract
import rx.lang.scala.Observable

/**
 * Market data line returned by call to [[IBClient]] method marketData.
 * This class is not intended to be instantiated by the user of [[IBClient]]
 */
case class MarketDataSubscription(ibclient: IBClient, id: Int, contract: Contract, observableTick: Observable[Tick]) {
  /**
   * Close this market data line and free all associated resources. There's no need to call any [[IBClient]] additional methods
   */
  def close(): Unit = {
    ibclient.closeMarketData(id)
  }
}
