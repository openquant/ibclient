package com.larroy.ibclient.handler

import com.larroy.ibclient.account.{AccountUpdate, AccountUpdateSubscription}
import rx.lang.scala.Subject

/**
 * @author piotr 08.04.15
 */
case class AccountUpdateHandler(accountUpdateSubscription: AccountUpdateSubscription, subject: Subject[AccountUpdate]) extends Handler {
  val nextAccountUpdate = new AccountUpdate()
  override def error(throwable: Throwable): Unit = {
    subject.onError(throwable)
  }
}
