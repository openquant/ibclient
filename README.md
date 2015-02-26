## Examples

ibclient.marketData(new FutureContract("BZ", "20150316", "NYMEX"))
val  h = Await.result(ibclient.historicalData(new StockContract("SPY"), endDate, 20,
DurationUnit.DAY, BarSize._15_mins, WhatToShow.MIDPOINT, false),  Duration.Inf)

