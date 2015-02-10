package com.larroy.trabot.ib.contract

import com.ib.client.Contract
import com.ib.client.Types.SecType

/**
 * @author piotr 10.02.15
 */
class FutureContract(symbol: String, expiry: String, exchange: String = "GLOBEX", currency: String = "USD") extends Contract {
  symbol(symbol)
  expiry(expiry)
  secType(SecType.FUT.name())
  exchange(exchange)
  currency(currency)
}
