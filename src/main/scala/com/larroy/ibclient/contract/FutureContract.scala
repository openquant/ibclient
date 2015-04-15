package com.larroy.ibclient.contract

import com.ib.client.Contract
import com.ib.client.Types.SecType


/**
 * @param symbol
 * @param expiry Expiry date in format yyyymmdd identifies the futures contract
 * @param exchange for example NYMEX for commodity futures
 * @param currency in which the future is denominated
 */
class FutureContract(symbol: String, expiry: String, exchange: String = "GLOBEX", currency: String = "USD") extends Contract {
  symbol(symbol)
  expiry(expiry)
  secType(SecType.FUT.name())
  exchange(exchange)
  currency(currency)
}
