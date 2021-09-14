package com.larroy.ibclient.order

import com.larroy.ibclient.order.kind.Kind


/**
 * Buy Order
 * @author piotr 19.02.15
 */
case class Buy(override val kind: Kind, override val quantity: Int, override val account: Option[String] = None) extends Order
