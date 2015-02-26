package com.larroy.ibclient.order

import com.larroy.ibclient.order.kind.Kind


/**
 * @author piotr 19.02.15
 */
case class Buy(override val kind: Kind, override val quantity: Int) extends Order
