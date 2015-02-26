package com.larroy.ibclient.contract

import com.ib.client.Contract
import com.ib.client.Types.SecType

/**
 * @author piotr 16.02.15
 */
class CashContract(symbol: String, localSymbol: String, exchange: String = "IDEALPRO", currency: String = "USD") extends Contract {
  symbol(symbol)
  localSymbol(localSymbol)
  secType(SecType.CASH.name())
  exchange(exchange)
  currency(currency)
}
