package com.larroy.ibclient
import com.larroy.ibclient.order.ExecutionStatus._

/**
 * Created by piotr on 6/13/15.
 */
case class OrderStatus(orderId: Int, status: ExecutionStatus, filled: Int, remaining: Int, avgFillPrice: Double,
                       permId: Int, parentId: Int, lastFilledPrice: Double, clientId: Int, whyHeld: String)
