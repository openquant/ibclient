# Scala IBClient

Interactive brokers Scala client.

This code wraps the java IBClient in asynchronous (futures) and reactive (RxScala) API.

All the wrapped functions from the java client return Futures, making the scala IBClient a
standalone class that plays nicely with concurrent code.


The Java IBClient is copyright interactive brokers and subject to it's license, see https://www.interactivebrokers.com/en/index.php?f=5041 and http://interactivebrokers.github.io/

## Build standalone jar

You can use a fat jar to run the commandline client by doing

```
sbt assembly
```

Which can be run with:

```
java -jar target/scala-2.12/ibclient-assembly-0.2.2-SNAPSHOT.jar
```

Or a binary executable with `sbt pack`, and run with `target/pack/bin/main`


## Examples

The client connects asynchronously so to wait until it's connected we can do the following:
For ilustration purposes we will block on the futures to make the API syncrhonous:

```
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
def block[A](f: Future[A]): A = {
    Await.result(f, Duration.Inf)
}

import com.larroy.ibclient.{IBClient}
val ibc = new IBClient("localhost", 7496, 3)
block(ibc.connect())
ibc.isConnected
```


Using the IBClient class is simple, historical data works out of the box and does throttling
automatically:

```


import com.larroy.ibclient.contract.{CashContract, FutureContract, GenericContract, StockContract}
val subscription = ibc.marketData(new StockContract("SPY"))
observableBar.subscribe({bar=>println(s"got bar ${bar}")}
```

```
import scala.concurrent.duration._
import org.joda.time._
import com.ib.client.Types.DurationUnit
import com.ib.client.Types.WhatToShow

val startDate = new DateTime(2015, 4, 10, 15, 0).toDate

val endDate = new DateTime(2015, 4, 13, 15, 0).toDate
val h = block(ibc.historicalData(new StockContract("SPY"), endDate, 20, DurationUnit.DAY, BarSize._15_mins, WhatToShow.MIDPOINT, false))

val h = block(ibclient.historicalData(new StockContract("SPY"), endDate, 20, 
    DurationUnit.DAY, BarSize._15_mins, WhatToShow.MIDPOINT, false))

```

Or with the 'easy historical data' facility:


```
val eh = block(ibc.easyHistoricalData(new StockContract("SPY"),startDate,endDate,BarSize._1_day, WhatToShow.TRADES))
```


```
val res2 = Await.result(ibclient.historicalData(new CashContract("EUR","EUR.USD"), endDate2, 120, DurationUnit.SECOND, BarSize._1_min, WhatToShow.MIDPOINT, false), scala.concurrent.duration.Duration.Inf)
import java.text.SimpleDateFormat
import java.util.Date
import com.larroy.ibclient.contract.{CashContract, StockContract}
import com.ib.client.Types.{BarSize, DurationUnit, WhatToShow, SecType}
import scala.concurrent.duration._
import scala.concurrent.Await
import com.larroy.ibclient._
import com.typesafe.config.ConfigFactory
val cfg = ConfigFactory.load().getConfig("ibclient.test")
Await.result(ibclient.connect(), Duration.Inf)
```

Subscribe to market data:

```
val subscription = ibclient.realtimeBars(new CashContract("EUR","EUR.USD"), WhatToShow.MIDPOINT)
subscription.observableBar.subscribe({bar=>println(s"got bar ${bar}")}, {error ⇒ throw (error)})
```


Get contract details

```
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.larroy.ibclient.contract.{CashContract, FutureContract, GenericContract, StockContract}
import com.larroy.ibclient.{IBClient}
val ibc = new IBClient("localhost", 7496, 3)
Await.result(ibc.connect(), Duration.Inf)
val c = new FutureContract("CL", "", "NYMEX")
val cd = Await.result(ibc.contractDetails(c), Duration.Inf)
```


Or with the commandline client:

```
history -c CL -t FUT -e NYMEX -x USD  -y 20150421 -a "20150128 15:00:00" -z "20150415 15:00:00"  -b
_1_min -o cl.csv 
```


```
history -c VIX -e CBOE -t IND -a "20161001 00:00:00" -o vix.csv -b _1_day
```

```
java -jar target/scala-2.11/ibclient-assembly-0.2.2-SNAPSHOT.jar history -c VIX -e CBOE -t IND -a
"20161115 00:00:00" -z "201118 00:00:00" -o vix.csv -b _1_day
```

