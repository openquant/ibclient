package com.larroy.ibclient

import com.ib.client.{Contract, OrderState}
import com.ib.client.{Order ⇒ IBOrder, _}

/**
 * Created by piotr on 6/13/15.
 */
case class OpenOrder(orderId: Int, contract: Contract, order: IBOrder, orderState: OrderState)
