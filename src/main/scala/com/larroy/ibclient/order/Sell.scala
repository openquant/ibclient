package com.larroy.ibclient.order

import com.larroy.ibclient.order.kind.Kind

/**
 * Sell order
 * @author piotr 19.02.15
 */
case class Sell(override val kind: Kind, override val quantity: Int) extends Order
