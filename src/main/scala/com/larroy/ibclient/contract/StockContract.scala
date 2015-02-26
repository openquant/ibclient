package com.larroy.ibclient.contract

import com.ib.client.Contract
import com.ib.client.Types.SecType

/**
 * @author piotr 10.02.15
 */
class StockContract(symbol: String, exchange: String = "SMART", currency: String = "USD") extends Contract {
  symbol(symbol)
  secType(SecType.STK.name())
  exchange(exchange)
  currency(currency)
}
