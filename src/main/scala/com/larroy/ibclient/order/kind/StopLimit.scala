package com.larroy.ibclient.order.kind

/**
 * @author piotr 19.02.15
 */

/**
 * @param stop stop price, in which the order is triggered
 * @param limit price limit
 */
case class StopLimit(stop: Double, limit: Double) extends Kind
