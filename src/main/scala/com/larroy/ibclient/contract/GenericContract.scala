package com.larroy.ibclient.contract

import com.ib.client.Contract
import com.ib.client.Types.SecType


// TODO: create case classes for other contract types

class GenericContract(
  sectTypeEnum: SecType,
  symbol: String,
  exchange: String = "IDEALPRO",
  currency: String = "USD") extends Contract {

  symbol(symbol)
  secType(sectTypeEnum.name())
  exchange(exchange)
  currency(currency)
}
