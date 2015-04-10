package com.larroy.ibclient.account

import com.ib.client.Contract

/**
 * @author piotr 08.04.15
 */
case class Position(
  contract: Contract,
  position: Int,
  marketPrice: Double,
  marketValue: Double,
  averageCost: Double,
  unrealizedPNL: Double,
  realizedPNL: Double,
  accountName: String)
