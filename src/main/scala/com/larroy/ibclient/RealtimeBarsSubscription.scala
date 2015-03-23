package com.larroy.ibclient

import com.ib.client.Contract
import rx.lang.scala.Observable

/**
 * Created by piotr on 23/03/15.
 */
case class RealtimeBarsSubscription (ibclient: IBClient, id: Int, contract: Contract, observableBar: Observable[Bar]) {
  def close(): Unit = {
    ibclient.closeRealtimeBar(id)
  }
}
