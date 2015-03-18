package com.larroy.ibclient

import java.util.concurrent.Executors
import java.util.{Collections, Date}

import com.ib.client.Types._
import com.ib.client.{Order ⇒ IBOrder, _}
import com.larroy.ibclient.handler._
import com.larroy.ibclient.order.Order
import com.larroy.ibclient.util.{HistoricalRequest, HistoricalRateLimiter, HistoryLimits}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTimeZone, DateTime}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import org.slf4j.{Logger, LoggerFactory}
import rx.lang.scala.subjects.PublishSubject
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise, Future}
import scala.util.{Success, Failure}

/**
 * The API is fully asynchronous and thread safe.
 * Most of the calls return a Future of the desired result that is eventually completed sucessfully or with
 * an error describing the problem.
 *
 * @param host host where TWS is running
 * @param port port configured in TWS API settings
 * @param clientId an integer to identify this client, a duplicated clientId will cause an error on connect
 */
class IBClient(val host: String, val port: Int, val clientId: Int) extends EWrapper {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val cfg = ConfigFactory.load().getConfig("ibclient")
  val eClientSocket = new EClientSocket(this)
  var reqId: Int = 0
  var orderId: Int = 0

  var errorCount: Int = 0
  var warnCount: Int = 0

  /**
   * A map of request id to Promise
   */
  val reqHandler = mutable.Map.empty[Int, Handler]
  val reqPromise = mutable.Map.empty[Int, AnyRef]

  private[this] var connectResult = Promise[Boolean]()

  private[this] var positionsPromise: Option[Promise[IndexedSeq[Position]]] = None
  private[this] var positionHandler: Option[PositionHandler] = None

  val historicalRateLimiter = new HistoricalRateLimiter
  val historicalExecutionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  /**
   * @return A Future[Boolean] that is completed once the client is connected and set to true. If it can't connect
   *         it will be set to false
   *
   * @example
   * {{{
   *        val ibclient = new IBClient("localhost", 7496, 1)
   *        val connected = Await.result(ibclient.connect(), testWaitDuration)
   * }}}
   */
  def connect(): Future[Boolean] = synchronized {
    if (eClientSocket.isConnected) {
      log.warn("connect: Client already connected")
      Future.successful[Boolean](true)
    } else {
      eClientSocket.eConnect(host, port, clientId)
      connectResult = Promise[Boolean]()
      connectResult.future
    }
  }

  /**
   * Disconnect from TWS
   */
  def disconnect(): Unit = synchronized {
    if (!eClientSocket.isConnected)
      log.warn("disconnect: Client is not connected")
    eClientSocket.eDisconnect()
  }

  /**
   * @return true if the client is connected
   */
  def isConnected: Boolean = eClientSocket.isConnected

  override def nextValidId(id: Int): Unit = synchronized {
    orderId = id
    reqId = orderId + 10000000
    log.debug(s"nextValidId: ${reqId}")
    connectResult.success(true)
  }

  /* connection and server ********************************************************************************/

  override def currentTime(time: Long): Unit = {
    log.debug(s"currentTime: ${time}")

  }

  override def connectionClosed(): Unit = {
    log.error(s"connectionClosed")
  }

  /* error and warnings handling ********************************************************************************/

  override def error(exception: Exception): Unit = synchronized {
    errorCount += 1
    log.error(s"error handler: ${exception.getMessage}")
    log.error(s"${exception.printStackTrace()}")
    connectResult.failure(exception)
    reqPromise.foreach { kv ⇒
      val promise = kv._2.asInstanceOf[Promise[_]]
      promise.failure(exception)
    }
    reqPromise.clear()
    reqHandler.foreach { kv ⇒ kv._2.error(exception)}
    reqHandler.clear()
    eClientSocket.eDisconnect()
  }

  override def error(id: Int, errorCode: Int, errorMsg: String): Unit = synchronized {
    if (errorCode > 2000) {
      warnCount += 1
      log.warn(s"Warning ${id} ${errorCode} ${errorMsg}")
    } else {
      errorCount += 1
      val errmsg = s"Error requestId: ${id} code: ${errorCode} msg: ${errorMsg}"
      log.error(errmsg)
      val apierror = new IBApiError(errmsg)
      if (id == -1) {
        // Error not specific to any request, these can be quite tricky to handle
        // if we were connecting we need to fail the connecting promise
        if (errorCode == 507)
          log.error("Check TWS logs, possible cause is duplicate client ID")
        if (connectResult.isCompleted) {
          // if we were not connecting we fail everything in flight
          // FIXME: improve in the case of connection lost / restored
          reqPromise.foreach { kv ⇒
            val promise = kv._2.asInstanceOf[Promise[_]]
            promise.failure(apierror)
          }
          reqPromise.clear()
          reqHandler.foreach { kv ⇒ kv._2.error(apierror)}
          reqHandler.clear()
        } else
          connectResult.failure(apierror)

      } else {
        // error specific to particular request with id: id
        reqPromise.remove(id).foreach { p =>
          log.error(s"Failing and removing promise: ${id}")
          val promise = p.asInstanceOf[Promise[_]]
          promise.failure(apierror)
        }
        reqHandler.remove(id).foreach { handler ⇒
          log.error(s"Propagating error and removing handler: ${id}")
          handler.error(apierror)
        }
      }
    }
  }

  override def error(str: String): Unit = synchronized {
    log.error(s"error handler: ${str}")
    errorCount += 1
  }


  /* fundamentals ********************************************************************************/

  /**
   * Request fundamentals
   * @param contract
   * @param typ any of ReportSnapshot, ReportsFinSummary, ReportRatios, ReportsFinStatements, RESC, CalendarReport
   * @return a future string, completed with the data
   */
  def fundamentals(contract: Contract, typ: FundamentalType): Future[String] = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")
    reqId += 1
    val promise = Promise[String]()
    reqPromise += (reqId → promise)
    eClientSocket.reqFundamentalData(reqId, contract, typ.getApiString)
    promise.future
  }

  /// EWrapper handlers
  override def fundamentalData(reqId: Int, data: String): Unit = synchronized {
    reqPromise.remove(reqId).foreach { x ⇒
      val promise = x.asInstanceOf[Promise[String]]
      promise.success(data)
    }
  }


  /* market data ********************************************************************************/


  /**
   * Request market data for the given contract
   * @param contract
   * @return a [[MarketDataSubscription]] which contains an Rx observable through which the [[Tick]] is delivered
   *         when is available. This allows to handle market data asynchronously by using reactive programming patterns.
   */
  def marketData(contract: Contract): MarketDataSubscription = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")
    reqId += 1
    eClientSocket.reqMktData(reqId, contract, "100,101,104,105,106,107,165,221,225,233,236,258,293,294,295,318", false,
      Collections.emptyList[TagValue]
    )
    val publishSubject = PublishSubject[Tick]()
    val subscription = new MarketDataSubscription(this, reqId, contract, publishSubject)
    val marketDataHandler = new MarketDataHandler(subscription, publishSubject)
    reqHandler += (reqId → marketDataHandler)
    log.debug(s"marketData reqId: ${reqId}")
    subscription
  }

  /**
   * Close a market data line
   * if there's no subscription with the given id this call has no effect
   *
   * @param id id of the [[MarketDataSubscription]]
   */
  def closeMarketData(id: Int): Unit = synchronized {
    reqHandler.remove(id).foreach { handler ⇒
      val marketDataHandler = handler.asInstanceOf[MarketDataHandler]
      eClientSocket.cancelMktData(id)
      log.debug(s"Closed market data line ${id}")
      marketDataHandler.subject.onCompleted()
    }
  }

  override def tickPrice(tickerId: Int, field: Int, price: Double, canAutoExecute: Int): Unit = synchronized {
    log.debug(s"tickPrice ${tickerId} ${field} ${price}")
    var handled = false
    reqHandler.get(tickerId).foreach { handler ⇒
      val marketDataHandler = handler.asInstanceOf[MarketDataHandler]
      marketDataHandler.subject.onNext(Tick(field, price))
      handled = true
    }
    if (!handled)
      log.debug(s"tickPrice ${tickerId} ignored, no handler exists for that tickerId")
  }

  override def tickSize(tickerId: Int, tickType: Int, size: Int): Unit = {
    log.debug(s"tickSize ${tickerId} ${tickType} ${size}")
    tickPrice(tickerId, tickType, size.toDouble, 0)
  }

  override def tickOptionComputation(tickerId: Int, field: Int, impliedVol: Double, delta: Double, optPrice: Double,
    pvDividend: Double, gamma: Double, vega: Double, theta: Double, undPrice: Double
  ): Unit = {
    log.debug(s"tickOptionComputation ${tickerId}")
  }

  override def tickGeneric(tickerId: Int, tickType: Int, value: Double): Unit = {
    log.debug(s"tickGeneric ${tickerId} ${tickType} ${value}")
    tickPrice(tickerId, tickType, value, 0)
  }

  // TODO
  override def tickString(tickerId: Int, tickType: Int, value: String): Unit = {
    log.debug(s"tickString ${tickerId} ${tickType} ${value}")
  }

  // TODO
  override def tickEFP(tickerId: Int, tickType: Int, basisPoints: Double, formattedBasisPoints: String,
    impliedFuture: Double, holdDays: Int, futureExpiry: String, dividendImpact: Double, dividendsToExpiry: Double
  ): Unit = {
    log.debug(s"tickEFP ${tickerId} ${tickType} ${basisPoints} ")
  }

  override def tickSnapshotEnd(reqId: Int): Unit = {
    log.debug(s"tickSnapshotEnd ${reqId}")
  }

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {
    log.debug(s"marketDataType ${reqId} ${marketDataType}")
  }

  /* orders ********************************************************************************/

  /**
   * Submit an order for the given contract
   * @param contract
   * @param order  @see [[order.Order]]
   */
  def placeOrder(contract: Contract, order: Order): Unit = synchronized {
    val iBOrder = order.toIBOrder
    if (iBOrder.orderId() == 0) {
      orderId += 1
      iBOrder.orderId(orderId)
    }
    eClientSocket.placeOrder(iBOrder.orderId(), contract, iBOrder)
  }

  /**
   * TODO FIXME
   */
  def openOrders(): Unit = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")
    eClientSocket.reqOpenOrders()
  }

  override def orderStatus(orderId: Int, status: String, filled: Int, remaining: Int, avgFillPrice: Double, permId: Int,
    parentId: Int, lastFillPrice: Double, clientId: Int, whyHeld: String
  ): Unit = synchronized {
    log.info(s"OrderStatus ${orderId} ${status}")
  }

  override def openOrder(orderId: Int, contract: Contract, order: IBOrder, orderState: OrderState): Unit = synchronized
  {
    log.info(s"openOrder ${orderId} ${contract} ${orderState}")
  }

  override def openOrderEnd(): Unit = synchronized {
    log.info(s"openOrderEnd")
  }

  override def deltaNeutralValidation(reqId: Int, underComp: DeltaNeutralContract): Unit = {}

  /* account and portfolio ********************************************************************************/
  // TODO

  override def updateAccountValue(key: String, value: String, currency: String, accountName: String): Unit = {}

  override def updatePortfolio(contract: Contract, position: Int, marketPrice: Double, marketValue: Double,
    averageCost: Double, unrealizedPNL: Double, realizedPNL: Double, accountName: String
  ): Unit = {}

  override def updateAccountTime(timeStamp: String): Unit = {}


  override def accountDownloadEnd(accountName: String): Unit = {
  }

  override def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit = {}

  override def accountSummaryEnd(reqId: Int): Unit = {}

  /* Positions ********************************************************************************/

  /**
   * Request info about positions @see [[Position]]
   * @return future of positions
   */
  def positions(): Future[IndexedSeq[Position]] = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")
    if (positionHandler.nonEmpty)
      log.warn("Positions request might be overlapping with previous one")
    positionHandler = Some(new PositionHandler())
    positionsPromise = Some(Promise[IndexedSeq[Position]]())
    log.debug("positions")
    eClientSocket.reqPositions()
    positionsPromise.get.future
  }

  override def position(account: String, contract: Contract, pos: Int, avgCost: Double): Unit = synchronized {
    positionHandler.foreach { handler ⇒
      handler.queue += new Position(account, contract, pos, avgCost)
    }
  }

  override def positionEnd(): Unit = synchronized {
    positionHandler.foreach { ph ⇒
      positionsPromise.foreach(_.success(ph.queue.toIndexedSeq))
    }
    positionHandler = None
  }

  /* contract details ********************************************************************************/

  /**
   * Get [[ib.client.ContractDetails]] for the given contract
   * @param contract
   * @return contract details for the given contract
   */
  def contractDetails(contract: Contract): Future[Seq[ContractDetails]] = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")
    reqId += 1
    val contractDetailsHandler = new ContractDetailsHandler()
    reqHandler += (reqId → contractDetailsHandler)
    val promise = Promise[IndexedSeq[ContractDetails]]()
    reqPromise += (reqId → promise)
    log.debug(s"reqContractDetails ${reqId}")
    eClientSocket.reqContractDetails(reqId, contract)
    promise.future
  }

  /// EWrapper handler
  override def contractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {
    log.debug(s"contractDetails ${reqId}")
    reqHandler.get(reqId).foreach { x ⇒
      val contractDetailsHandler = x.asInstanceOf[ContractDetailsHandler]
      contractDetailsHandler.details += contractDetails
    }
  }

  /// EWrapper handler
  override def contractDetailsEnd(reqId: Int): Unit = {
    log.debug(s"contractDetailsEnd ${reqId}")
    reqHandler.remove(reqId).foreach { h ⇒
      val contractDetailsHandler = h.asInstanceOf[ContractDetailsHandler]
      reqPromise.remove(reqId).foreach { p ⇒
        val promise = p.asInstanceOf[Promise[IndexedSeq[ContractDetails]]]
        promise.success(contractDetailsHandler.details)
      }
    }
  }

  override def bondContractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {
    this.contractDetails(reqId, contractDetails)
  }

  /* executions ********************************************************************************/
  // TODO

  override def execDetails(reqId: Int, contract: Contract, execution: Execution): Unit = {}

  override def execDetailsEnd(reqId: Int): Unit = {}

  override def commissionReport(commissionReport: CommissionReport): Unit = {}

  /* market depth ********************************************************************************/
  // TODO

  override def updateMktDepthL2(tickerId: Int, position: Int, marketMaker: String, operation: Int, side: Int,
    price: Double, size: Int
  ): Unit = {}

  override def updateMktDepth(tickerId: Int, position: Int, operation: Int, side: Int, price: Double, size: Int
  ): Unit = {}

  /* news bulletins ********************************************************************************/
  // TODO

  override def updateNewsBulletin(msgId: Int, msgType: Int, message: String, origExchange: String): Unit = {}

  /* financial advisors ********************************************************************************/
  // TODO

  override def managedAccounts(accountsList: String): Unit = {}

  override def receiveFA(faDataType: Int, xml: String): Unit = {}

  /* historical data ********************************************************************************/
  /**
   * Retrieve historical data between two dates respecting ib rate limitations:
   * https://www.interactivebrokers.com/en/software/api/apiguide/tables/historical_data_limitations.htm
   *
   * The request is translated internally to potentially several historicalData requests and the future result is fullfilled
   * on successful completion.
   *
   * If any request fails, the result is a failed future.
   *
   * @param contract
   * @param startDate
   * @param endDate
   * @param barSize  span of tone bar
   *                 one of [_1_secs, _5_secs, _10_secs, _15_secs, _30_secs, _1_min, _2_mins, _3_mins, _5_mins, _10_mins, _15_mins, _20_mins, _30_mins, _1_hour, _4_hours, _1_day, _1_week]
   * @param whatToShow Determines the nature of data being extracted.
   *                   One of: [TRADES, MIDPOINT, BID, ASK] for realtime bars and [BID_ASK, HISTORICAL_VOLATILITY, OPTION_IMPLIED_VOLATILITY, YIELD_ASK, YIELD_BID, YIELD_BID_ASK, YIELD_LAST]
   * @param rthOnly only data from regular trading hours if true
   * @param ctx execution context where futures for intermediate requests are scheduled when they have to be deferred
   * @return
   */
  def easyHistoricalData(
    contract: Contract,
    startDate: Date,
    endDate: Date,
    barSize: BarSize,
    whatToShow: WhatToShow,
    rthOnly: Boolean = false): Future[IndexedSeq[Bar]] = synchronized {

    val historyDuration = HistoryLimits.bestDuration(startDate, endDate, barSize)
    val durationUnit = historyDuration.durationUnit
    val endDatesDurations = historyDuration.endDates(endDate).zip(historyDuration.durations)
    val resultPromise = Promise[IndexedSeq[Bar]]()
    class RetryException extends Exception()
    val historyRequestAggregation = new Runnable {
      def run(): Unit = {
        val cumResult = mutable.Queue.empty[Bar]
        log.debug(s"easyHistoricalData, durations: ${endDatesDurations}")
        endDatesDurations.reverseIterator.foreach { dateDuration ⇒
          val endDate = dateDuration._1
          val duration = dateDuration._2
          val request = new HistoricalRequest(contract.symbol, contract.exchange, endDate, durationUnit, barSize, duration)
          val nextAfter_ms = historicalRateLimiter.registerAndGetWait_ms(request)
          if (nextAfter_ms > 0)
            Thread.sleep(nextAfter_ms)

          util.retry(cfg.as[Int]("historyRequestPacingViolationRetry.count")) {
            val barsFuture = historicalData(contract, endDate, duration, durationUnit, barSize, whatToShow, rthOnly, false)
            Await.ready(barsFuture, Duration(cfg.as[Int]("historyRequestTimeout.length"), cfg.as[String]("historyRequestTimeout.unit")))
            barsFuture.value match {
              // Promise not completed, timeout
              case None ⇒ resultPromise.failure(new IBClientError(s"History request timeout ${request}"))

              // No Data
              case Some(Failure(error: IBApiError)) if error.code == 162 && error.msg.matches("Historical Market Data Service error message:HMDS query returned no data.*") ⇒ {
                log.warn(s"easyHistoricalData: History request ${request} returned no data")
              }

              // Pacing violation
              case Some(Failure(error: IBApiError)) if error.code == 162 && error.msg.matches("Historical Market Data Service error message:Historical data request pacing violation.*") ⇒ {
                val waitTime = Duration(cfg.as[Int]("historyrequestpacingviolationretry.length"), cfg.as[String]("historyrequestpacingviolationretry.unit"))
                log.warn(s"easyHistoricalData: Pacing violation for request: ${request}, suspending for: ${waitTime}")
                Thread.sleep(waitTime.toMillis)
                throw new RetryException
              }

              // Other failure, but we have some results
              case Some(Failure(error)) if cumResult.isEmpty ⇒ resultPromise.failure(error)

              // Other failure, no results
              case Some(Failure(error)) ⇒ log.warn(s"Partial history request failure: ${request} this means some requests failed but others succeeded")

              // Success
              case Some(Success(bars)) ⇒ cumResult ++= bars.reverse
            }
          }
        }
        resultPromise.success(cumResult.reverseIterator.toVector)
      }
    }
    historicalExecutionContext.execute(historyRequestAggregation)
    resultPromise.future

    /*
    def throttledRequest(endDate: Date, duration: Int): Future[IndexedSeq[Bar]] = {
      val request = new HistoricalRequest(contract.symbol, contract.exchange, endDate, durationUnit, barSize, duration)
      def doRequest = {
        val res = historicalData(contract, endDate, duration, durationUnit, barSize, whatToShow, rthOnly, false)
        historicalRateLimiter.requested(request)
        res
      }

      val nextAfter_ms = historicalRateLimiter.nextRequestAfter_ms(request)
      if (nextAfter_ms > 0) {
        log.debug(s"historicalData (deferring ${nextAfter_ms} ms) ${contract.symbol} ${duration} ${durationUnit} barSize: ${barSize}")
        util.defer(nextAfter_ms) {
          log.debug(s"historicalData (deferred ${nextAfter_ms} ms) ${contract.symbol} ${duration} ${durationUnit} barSize: ${barSize}")
          doRequest
        }(ctx).flatMap(identity)
      } else {
        log.debug(s"historicalData ${contract.symbol} ${duration} ${durationUnit} barSize: ${barSize}")
        doRequest
      }
    }

    val partialResults = ArrayBuffer.empty[Future[IndexedSeq[Bar]]]
   .foreach { dateDuration ⇒
      partialResults.foreach { partialResult ⇒
        println(s"${partialResult}")
        if (partialResult.isCompleted) {
          partialResult.value match {
            case Some(Failure(e)) ⇒ {
              log.error("Request failed!")
              return Promise[mutable.IndexedSeq[Bar]].failure(e).future
            }
            case _ ⇒
          }

        }
      }
      partialResults += throttledRequest(dateDuration._1, dateDuration._2)
    }
    historicalRateLimiter.cleanup()

    val result = Future.sequence(partialResults).map { x ⇒ x.flatMap(identity) }
    result
    */
  }


  /**
   * Request historical data for a given contract.
   *
   * This calls once to the underlying EClientSocket, meaning that not all combinations of durationUnit, barSize and duration
   * are legal, plus history requests are limited by
   * [[https://www.interactivebrokers.com/en/software/api/apiguide/tables/historical_data_limitations.htm]]
   *
   * @see [[util.HistoryLimits]] to retrieve maximum duration for a given combination
   *
   * @param contract
   * @param endDate
   * @param duration number of durationUnit to request
   * @param durationUnit time span the request will cover one of [SECOND, DAY, WEEK, MONTH, YEAR] see com.ib.client.Types.DurationUnit
   * @param barSize  span of tone bar
   *                 one of [_1_secs, _5_secs, _10_secs, _15_secs, _30_secs, _1_min, _2_mins, _3_mins, _5_mins, _10_mins, _15_mins, _20_mins, _30_mins, _1_hour, _4_hours, _1_day, _1_week]
   * @param whatToShow Determines the nature of data being extracted.
   *                   One of: [TRADES, MIDPOINT, BID, ASK] for realtime bars and [BID_ASK, HISTORICAL_VOLATILITY, OPTION_IMPLIED_VOLATILITY, YIELD_ASK, YIELD_BID, YIELD_BID_ASK, YIELD_LAST]
   * @param rthOnly only data from regular trading hours if true
   * @param rateLimit set to false to skip rate limiting **warning** this can cause pacing violation errors
   * @return future of IndexedSeq of [[Bar]]
   */
  def historicalData(contract: Contract, endDate: Date, duration: Int,
    durationUnit: DurationUnit, barSize: BarSize, whatToShow: WhatToShow, rthOnly: Boolean = false, rateLimit: Boolean = true
  ): Future[IndexedSeq[Bar]] = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")

    reqId += 1
    reqHandler += (reqId → new HistoricalDataHandler())
    val promise = Promise[IndexedSeq[Bar]]()
    reqPromise += (reqId → promise)

    val durationStr = duration + " " + durationUnit.toString().charAt(0)
    val dateTime = new DateTime(endDate, DateTimeZone.UTC)
    // format yyyymmdd hh:mm:ss tmz, where the time zone is allowed (optionally) after a space at the end.
    val dateStr = DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss z").print(dateTime)
    val request = new HistoricalRequest(contract.symbol, contract.exchange, endDate, durationUnit, barSize, duration)

    def doRequest = {
      log.debug(s"reqHistoricalData reqId: ${reqId} endDate: ${dateStr} symbol: ${contract.symbol} duration: ${duration} barSize: ${barSize}")
      eClientSocket.reqHistoricalData(reqId, contract, dateStr, durationStr, barSize.toString, whatToShow.toString,
        if (rthOnly) 1 else 0, 2, Collections.emptyList[TagValue])
    }

    if (rateLimit) {
      val nextAfter_ms = historicalRateLimiter.registerAndGetWait_ms(request)
      if (nextAfter_ms > 0) {
        log.debug(s"rate limiting historicalData, deferring ${nextAfter_ms} ms")
        util.defer(nextAfter_ms) {
          doRequest
        }(historicalExecutionContext)
      } else {
        doRequest
      }
      historicalRateLimiter.cleanup()
    } else {
      doRequest
    }
    promise.future
  }

  /// EWrapper handlers
  override def historicalData(
    reqId: Int,
    date: String,
    open: Double, high: Double, low: Double, close: Double,
    volume: Int,
    count: Int,
    wap: Double,
    hasGaps: Boolean
  ): Unit = {
    /*  Even though we specify formatDate as 2 which accordint to the API should always return seconds since the epoch it's not respected
        When asking for DurationUnit.DAY and BarSize._1_day  we get dates in yyyyymmdd format
     */

    //log.debug(s"historicalData ${reqId} ${date}")
    reqHandler.get(reqId).foreach { x =>
      val handler = x.asInstanceOf[HistoricalDataHandler]
      if (date.startsWith("finished")) {
        reqPromise.remove(reqId).foreach { p ⇒
          val promise = p.asInstanceOf[Promise[IndexedSeq[Bar]]]
          promise.success(handler.queue.toIndexedSeq)
        }
        reqHandler.remove(reqId)
      } else {
        handler.queue += new Bar(util.dateEpoch_s(date), high, low, open, close, volume, count, wap, hasGaps)
      }
    }
  }


  /* market scanners ********************************************************************************/
  // TODO

  override def scannerParameters(xml: String): Unit = {}

  override def scannerData(reqId: Int, rank: Int, contractDetails: ContractDetails, distance: String, benchmark: String,
    projection: String, legsStr: String
  ): Unit = {}

  override def scannerDataEnd(reqId: Int): Unit = {}


  /* realtime bars ********************************************************************************/
  // TODO

  override def realtimeBar(reqId: Int, time: Long, open: Double, high: Double, low: Double, close: Double, volume: Long,
    wap: Double, count: Int
  ): Unit = {}

  /* display groups ********************************************************************************/
  // TODO

  override def displayGroupList(reqId: Int, groups: String): Unit = {}

  override def displayGroupUpdated(reqId: Int, contractInfo: String): Unit = {}

  /* ********************************************************************************/
  // TODO

  override def verifyAndAuthMessageAPI(apiData: String, xyzChallange: String): Unit = {}

  override def verifyCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  /* ********************************************************************************/
  // TODO

  override def verifyAndAuthCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  override def verifyMessageAPI(apiData: String): Unit = {}
}
