package com.larroy.ibclient

import com.ib.client.Contract
import rx.lang.scala.Observable

/**
 * This class represents a market data line (realtimeBar) for a given contract. It's returned by a call
 * to the realtimeBars method of [[IBClient]]. It's not to be instatiated by the user of [[IBClient]]
 * @param ibclient
 * @param id request Id of this subscription
 * @param contract
 * @param observableBar
 */
case class RealtimeBarsSubscription (ibclient: IBClient, id: Int, contract: Contract, observableBar: Observable[Bar]) {
  /**
   * Close this subscription freeing resources and closing the market data line if there's any
   * There's no need to call any [[IBClient]] additional methods after this call is made.
   */
  def close(): Unit = {
    ibclient.closeRealtimeBar(id)
  }
}
