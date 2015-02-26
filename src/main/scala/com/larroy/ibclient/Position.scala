package com.larroy.ibclient

import com.ib.client.Contract

/**
 * @author piotr 19.02.15
 */
case class Position(account: String, contract: Contract, position: Int, avgCost: Double)
