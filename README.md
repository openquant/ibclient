## Examples

ibclient.marketData(new FutureContract("BZ", "20150316", "NYMEX"))
val  h = Await.result(ibclient.historicalData(new StockContract("SPY"), endDate, 20,
DurationUnit.DAY, BarSize._15_mins, WhatToShow.MIDPOINT, false),  Duration.Inf)

val res2 = Await.result(ibclient.historicalData(new CashContract("EUR","EUR.USD"), endDate2, 120, DurationUnit.SECOND, BarSize._1_min, WhatToShow.MIDPOINT, false), scala.concurrent.duration.Duration.Inf)

