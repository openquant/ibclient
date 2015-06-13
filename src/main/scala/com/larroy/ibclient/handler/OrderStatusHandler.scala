package com.larroy.ibclient.handler

import com.larroy.ibclient.OrderStatus
import rx.lang.scala.Subject

/**
 * Created by piotr on 6/13/15.
 */
case class OrderStatusHandler(subject: Subject[OrderStatus]) extends Handler {

}
