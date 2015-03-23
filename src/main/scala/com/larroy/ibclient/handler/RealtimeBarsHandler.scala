package com.larroy.ibclient.handler

import com.larroy.ibclient.{Bar, RealtimeBarsSubscription}
import rx.lang.scala.Subject

/**
 * Created by piotr on 23/03/15.
 */
case class RealtimeBarsHandler(subscription: RealtimeBarsSubscription, subject: Subject[Bar]) extends Handler {
  override def error(throwable: Throwable): Unit = {
    subject.onError(throwable)
  }
}
