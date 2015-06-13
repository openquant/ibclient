package com.larroy.ibclient.handler

import com.larroy.ibclient.OpenOrder

import scala.collection.mutable

/**
 * @author piotr 20.02.15
 */
case class OpenOrdersHandler(openOrders: mutable.Map[Int, OpenOrder] = mutable.Map.empty[Int, OpenOrder]) extends Handler
