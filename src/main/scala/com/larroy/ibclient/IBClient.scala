package com.larroy.ibclient

import java.util.{Collections, Calendar, Date}

import com.ib.client.Types._
import com.ib.client.{Order ⇒ IBOrder, _}
import com.larroy.ibclient.handler._
import com.larroy.ibclient.handler._
import com.larroy.ibclient.order.Buy
import com.larroy.ibclient.order.Order

import org.slf4j.{Logger, LoggerFactory}
import rx.lang.scala.Subject
import rx.lang.scala.subjects.PublishSubject
import scala.collection.mutable
import scala.concurrent.{Promise, Future}

object APIState extends Enumeration {
  type APIState = Value
  val WaitForConnection, Connected, Disconnected = Value
}

object IBClient {
  def dateEpoch_s(date: String): Long = {
    if (date.length() == 8) {
      val year = date.substring(0, 4).toInt
      val month = date.substring(4, 6).toInt
      val day = date.substring(6).toInt
      val cal = Calendar.getInstance()
      cal.set(year, month, day)
      cal.getTime().getTime() / 1000
    } else
      date.toLong
  }
}

/**
 * @author piotr 19.10.14
 */
class IBClient(val host: String, val port: Int, val clientId: Int) extends EWrapper {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
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


  def connect(): Future[Boolean] = synchronized {
    if (eClientSocket.isConnected) {
      log.warn("connect: Client already connected")
      Future.successful[Boolean](true)
    } else {
      eClientSocket.eConnect(host, port, clientId)
      connectResult.future
    }
  }

  def disconnect(): Unit = synchronized {
    if (!eClientSocket.isConnected)
      log.warn("disconnect: Client is not connected")
    eClientSocket.eDisconnect()
  }

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
    reqHandler.foreach { kv ⇒ kv._2.error(exception)}
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
        // if we were connecting we need to fail the connecting promise
        if (errorCode == 507)
          log.error("Check TWS logs, possible cause is duplicate client ID")
        connectResult.failure(apierror)
      } else {
        reqPromise.remove(id).foreach { p =>
          log.error(s"Failing and removing promise: ${id}")
          val promise = p.asInstanceOf[Promise[_]]
          promise.failure(apierror)
        }
        reqHandler.remove(id).foreach { handler ⇒
          log.error(s"Propagating error to and removing handler: ${id}")
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

  override def tickString(tickerId: Int, tickType: Int, value: String): Unit = {
    log.debug(s"tickString ${tickerId} ${tickType} ${value}")
  }

  override def tickEFP(tickerId: Int, tickType: Int, basisPoints: Double, formattedBasisPoints: String,
    impliedFuture: Double, holdDays: Int, futureExpiry: String, dividendImpact: Double, dividendsToExpiry: Double
  ): Unit = {
    log.debug(s"tickEFP ${tickerId} ${tickType} ${basisPoints} ")
  }

  override def tickSnapshotEnd(reqId: Int): Unit = {}

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {
    log.debug(s"marketDataType ${reqId} ${marketDataType}")

  }

  /* orders ********************************************************************************/

  def placeOrder(contract: Contract, order: Order): Unit = synchronized {
    val iBOrder = order.toIBOrder
    if (iBOrder.orderId() == 0) {
      orderId += 1
      iBOrder.orderId(orderId)
    }
    eClientSocket.placeOrder(iBOrder.orderId(), contract, iBOrder)
  }

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

  override def openOrder(orderId: Int, contract: Contract, order: IBOrder, orderState: OrderState): Unit = synchronized {
    log.info(s"openOrder ${orderId} ${contract} ${orderState}")
  }

  override def openOrderEnd(): Unit = synchronized {
    log.info(s"openOrderEnd")
  }

  // nextValidId handled after connect up

  override def deltaNeutralValidation(reqId: Int, underComp: DeltaNeutralContract): Unit = {}

  /* account and portfolio ********************************************************************************/
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

  /// EWrapper handlers
  override def contractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {
    log.debug(s"contractDetails ${reqId}")
    reqHandler.get(reqId).foreach { x ⇒
      val contractDetailsHandler = x.asInstanceOf[ContractDetailsHandler]
      contractDetailsHandler.details += contractDetails
    }
  }

  /// EWrapper handlers
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

  override def execDetails(reqId: Int, contract: Contract, execution: Execution): Unit = {}

  override def execDetailsEnd(reqId: Int): Unit = {}

  override def commissionReport(commissionReport: CommissionReport): Unit = {}

  /* market depth ********************************************************************************/

  override def updateMktDepthL2(tickerId: Int, position: Int, marketMaker: String, operation: Int, side: Int,
    price: Double, size: Int
  ): Unit = {}

  override def updateMktDepth(tickerId: Int, position: Int, operation: Int, side: Int, price: Double, size: Int
  ): Unit = {}

  /* news bulletins ********************************************************************************/

  override def updateNewsBulletin(msgId: Int, msgType: Int, message: String, origExchange: String): Unit = {}

  /* financial advisors ********************************************************************************/

  override def managedAccounts(accountsList: String): Unit = {}

  override def receiveFA(faDataType: Int, xml: String): Unit = {}

  /* historical data ********************************************************************************/

  /**
   * Request historical data for a given contract
   * @param contract
   * @param endDate format yyyymmdd hh:mm:ss tmz, where the time zone is allowed (optionally) after a space at the end.
   * @param duration number of durationUnit to request
   * @param durationUnit time span the request will cover one of [SECOND, DAY, WEEK, MONTH, YEAR] see [[DurationUnit]]
   * @param barSize  span of tone bar
   *                 one of [_1_secs, _5_secs, _10_secs, _15_secs, _30_secs, _1_min, _2_mins, _3_mins, _5_mins, _10_mins, _15_mins, _20_mins, _30_mins, _1_hour, _4_hours, _1_day, _1_week]
   * @param whatToShow Determines the nature of data being extracted.
   *                   One of: [TRADES, MIDPOINT, BID, ASK] for realtime bars and [BID_ASK, HISTORICAL_VOLATILITY, OPTION_IMPLIED_VOLATILITY, YIELD_ASK, YIELD_BID, YIELD_BID_ASK, YIELD_LAST]
   * @param rthOnly only data from regular trading hours if true
   * @return
   */
  def historicalData(contract: Contract, endDate: String, duration: Int,
    durationUnit: DurationUnit, barSize: BarSize, whatToShow: WhatToShow, rthOnly: Boolean
  ): Future[IndexedSeq[Bar]] = synchronized {
    if (!eClientSocket.isConnected)
      throw new IBApiError("marketData: Client is not connected")

    reqId += 1
    reqHandler += (reqId → new HistoricalDataHandler())
    val promise = Promise[IndexedSeq[Bar]]()
    reqPromise += (reqId → promise)

    val durationStr = duration + " " + durationUnit.toString().charAt(0)
    log.debug(s"reqHistoricalData ${reqId}")
    eClientSocket.reqHistoricalData(reqId, contract, endDate, durationStr, barSize.toString, whatToShow.toString,
      if (rthOnly) 1 else 0, 2, Collections.emptyList[TagValue]
    )

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
    //log.debug(s"historicalData ${reqId}")
    reqHandler.get(reqId).foreach { x =>
      val handler = x.asInstanceOf[HistoricalDataHandler]
      if (date.startsWith("finished")) {
        reqPromise.remove(reqId).foreach { p ⇒
          val promise = p.asInstanceOf[Promise[IndexedSeq[Bar]]]
          promise.success(handler.queue.toIndexedSeq)
        }
        reqHandler.remove(reqId)
      } else {
        val longDate = IBClient.dateEpoch_s(date)
        handler.queue += new Bar(longDate, high, low, open, close, volume, count, wap, hasGaps)
      }
    }
  }


  /* market scanners ********************************************************************************/

  override def scannerParameters(xml: String): Unit = {}

  override def scannerData(reqId: Int, rank: Int, contractDetails: ContractDetails, distance: String, benchmark: String,
    projection: String, legsStr: String
  ): Unit = {}

  override def scannerDataEnd(reqId: Int): Unit = {}


  /* realtime bars ********************************************************************************/

  override def realtimeBar(reqId: Int, time: Long, open: Double, high: Double, low: Double, close: Double, volume: Long,
    wap: Double, count: Int
  ): Unit = {}

  /* display groups ********************************************************************************/

  override def displayGroupList(reqId: Int, groups: String): Unit = {}

  override def displayGroupUpdated(reqId: Int, contractInfo: String): Unit = {}

  /* ********************************************************************************/

  override def verifyAndAuthMessageAPI(apiData: String, xyzChallange: String): Unit = {}

  override def verifyCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  /* ********************************************************************************/

  override def verifyAndAuthCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  override def verifyMessageAPI(apiData: String): Unit = {}
}
