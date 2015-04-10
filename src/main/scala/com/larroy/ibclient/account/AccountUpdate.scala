package com.larroy.ibclient.account

import scala.collection.mutable
/**
 * @author piotr 08.04.15
 */
case class AccountUpdate(
  positions: mutable.ArrayBuffer[Position] = mutable.ArrayBuffer.empty[Position],
  accountInfo: mutable.Map[String, Value] = mutable.Map.empty[String,Value],
  netLiquidation: Value = Value(-1, "USD")
)

