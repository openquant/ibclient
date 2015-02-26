package com.larroy.ibclient.order.kind

/**
 * @author piotr 20.02.15
 */
case class TrailLimitIfTouched(stop: Double, limit: Double, trail: Double) extends Kind
