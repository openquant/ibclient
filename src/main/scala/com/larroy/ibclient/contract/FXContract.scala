package com.larroy.ibclient.contract

import com.ib.client.Contract
import com.ib.client.Types.SecType

// TODO: add more params

/**
 * @param symbol
 */
class FXContract(symbol: String) extends Contract {
  symbol(symbol)
  secType(SecType.CASH.name())
  exchange("IDEALPRO")
  currency("GBP")
}
