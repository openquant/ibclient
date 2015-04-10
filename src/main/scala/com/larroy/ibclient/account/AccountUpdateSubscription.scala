package com.larroy.ibclient.account

import com.larroy.ibclient.IBClient
import rx.lang.scala.Observable
/**
 * @author piotr 08.04.15
 */
case class AccountUpdateSubscription(iBClient: IBClient, observableAccountUpdate: Observable[AccountUpdate]) {

  def close(): Unit = {
    iBClient.closeAccountUpdateSubscription()
  }
}
