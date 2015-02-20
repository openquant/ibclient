package com.larroy.trabot.ib.order

import com.larroy.trabot.ib.order.kind.Kind


/**
 * @author piotr 19.02.15
 */
case class Buy(override val kind: Kind, override val quantity: Int) extends Order
