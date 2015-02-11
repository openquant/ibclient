package com.larroy.trabot.ib

import java.util
import java.util.{Collections, Calendar, Date}

import com.ib.client.Types._
import com.ib.client._

import org.slf4j.{Logger, LoggerFactory}
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.{Promise, Future}

object APIState extends Enumeration {
  type APIState = Value
  val WaitForConnection, Connected, Disconnected = Value
}

//import APIState._

trait Handler

case class HistoricalDataHandler(queue: mutable.Queue[Bar] = mutable.Queue.empty[Bar]) extends Handler

//promise: Promise[IndexedSeq[Bar]] = Promise[IndexedSeq[Bar]]()

case class ContractDetailsHandler(
  details: mutable.ArrayBuffer[ContractDetails] = mutable.ArrayBuffer.empty[ContractDetails]
) extends Handler

//promise: Promise[IndexedSeq[ContractDetails]] = Promise[IndexedSeq[ContractDetails]]()

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

  private[this] var connectResult: Promise[Boolean] = null

  def connect(): Future[Boolean] = {
    connectResult = Promise[Boolean]()
    eClientSocket.eConnect(host, port, clientId)
    connectResult.future
  }

  def disconnect(): Unit = {
    eClientSocket.eDisconnect()
    connectResult = null
  }

  override def nextValidId(id: Int): Unit = {
    orderId = id
    reqId = orderId + 10000000
    log.debug(s"nextValidId: ${reqId}")
    connectResult.success(true)
  }

  /* error and warnings handling ********************************************************************************/

  override def error(e: Exception): Unit = {
    errorCount += 1
    log.error(s"error handler: ${e.getMessage}")
    log.error(s"${e.printStackTrace()}")
    if (connectResult != null)
      connectResult.failure(e)
  }

  override def error(id: Int, errorCode: Int, errorMsg: String): Unit = {
    if (errorCode > 2000) {
      warnCount += 1
      log.warn(s"Warning ${id} ${errorCode} ${errorMsg}")
    } else {
      errorCount += 1
      log.error(s"Error ${id} ${errorCode} ${errorMsg}")
      log.error(s"${eClientSocket.isConnected}")
      reqPromise.remove(id).foreach { p =>
        log.error(s"failing pending request ${id}")
        val promise = p.asInstanceOf[Promise[_]]
        promise.failure(new IBApiError(s"code: ${errorCode} msg: ${errorMsg}"))
      }
      reqHandler -= id
    }
  }

  override def error(str: String): Unit = {
    log.error(s"error handler: ${str}")
    errorCount += 1
  }

  /* contract details ********************************************************************************/

  /**
   * @param contract
   * @return contract details for the given contract
   */
  def contractDetails(contract: Contract): Future[Seq[ContractDetails]] = {
    reqId += 1
    val contractDetailsHandler = new ContractDetailsHandler()
    reqHandler += (reqId → contractDetailsHandler)
    val promise = Promise[IndexedSeq[ContractDetails]]()
    reqPromise += (reqId → promise)
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

  /* fundamentals ********************************************************************************/

  def fundamentals(contract: Contract, typ: FundamentalType): Future[String] = {
    reqId += 1
    val promise = Promise[String]()
    reqPromise += (reqId → promise)
    eClientSocket.reqFundamentalData(reqId, contract, typ.getApiString)
    promise.future
  }

  /// EWrapper handlers
  override def fundamentalData(reqId: Int, data: String): Unit = {
    reqPromise.remove(reqId).foreach { x ⇒
      val promise = x.asInstanceOf[Promise[String]]
      promise.success(data)
    }
  }

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
  ): Future[IndexedSeq[Bar]] = {
    reqId += 1
    reqHandler += (reqId → new HistoricalDataHandler())
    val promise = Promise[IndexedSeq[Bar]]()
    reqPromise += (reqId → promise)

    val durationStr = duration + " " + durationUnit.toString().charAt(0)
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

  override def accountDownloadEnd(accountName: String): Unit = {
  }

  override def bondContractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {
  }

  override def managedAccounts(accountsList: String): Unit = {}

  override def verifyAndAuthMessageAPI(apiData: String, xyzChallange: String): Unit = {}

  override def displayGroupList(reqId: Int, groups: String): Unit = {}

  override def receiveFA(faDataType: Int, xml: String): Unit = {}

  override def tickSnapshotEnd(reqId: Int): Unit = {}

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {}

  override def updateMktDepthL2(tickerId: Int, position: Int, marketMaker: String, operation: Int, side: Int,
    price: Double, size: Int
  ): Unit = {}

  override def position(account: String, contract: Contract, pos: Int, avgCost: Double): Unit = {}

  override def verifyCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  override def scannerDataEnd(reqId: Int): Unit = {}

  override def deltaNeutralValidation(reqId: Int, underComp: DeltaNeutralContract): Unit = {}

  override def execDetailsEnd(reqId: Int): Unit = {}

  override def orderStatus(orderId: Int, status: String, filled: Int, remaining: Int, avgFillPrice: Double, permId: Int,
    parentId: Int, lastFillPrice: Double, clientId: Int, whyHeld: String
  ): Unit = {}

  override def tickString(tickerId: Int, tickType: Int, value: String): Unit = {}


  override def tickSize(tickerId: Int, field: Int, size: Int): Unit = {}

  override def accountSummaryEnd(reqId: Int): Unit = {}

  override def tickOptionComputation(tickerId: Int, field: Int, impliedVol: Double, delta: Double, optPrice: Double,
    pvDividend: Double, gamma: Double, vega: Double, theta: Double, undPrice: Double
  ): Unit = {}

  override def currentTime(time: Long): Unit = {}

  override def updateNewsBulletin(msgId: Int, msgType: Int, message: String, origExchange: String): Unit = {}

  override def openOrder(orderId: Int, contract: Contract, order: Order, orderState: OrderState): Unit = {}

  override def scannerParameters(xml: String): Unit = {}

  override def updateMktDepth(tickerId: Int, position: Int, operation: Int, side: Int, price: Double, size: Int
  ): Unit = {}

  override def updateAccountTime(timeStamp: String): Unit = {}

  override def connectionClosed(): Unit = {}

  override def realtimeBar(reqId: Int, time: Long, open: Double, high: Double, low: Double, close: Double, volume: Long,
    wap: Double, count: Int
  ): Unit = {}

  override def tickGeneric(tickerId: Int, tickType: Int, value: Double): Unit = {}

  override def updatePortfolio(contract: Contract, position: Int, marketPrice: Double, marketValue: Double,
    averageCost: Double, unrealizedPNL: Double, realizedPNL: Double, accountName: String
  ): Unit = {}

  override def verifyAndAuthCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  override def updateAccountValue(key: String, value: String, currency: String, accountName: String): Unit = {}

  override def tickEFP(tickerId: Int, tickType: Int, basisPoints: Double, formattedBasisPoints: String,
    impliedFuture: Double, holdDays: Int, futureExpiry: String, dividendImpact: Double, dividendsToExpiry: Double
  ): Unit = {}

  override def positionEnd(): Unit = {}

  override def openOrderEnd(): Unit = {}

  override def verifyMessageAPI(apiData: String): Unit = {}

  override def execDetails(reqId: Int, contract: Contract, execution: Execution): Unit = {}

  override def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit = {}

  override def commissionReport(commissionReport: CommissionReport): Unit = {}

  override def tickPrice(tickerId: Int, field: Int, price: Double, canAutoExecute: Int): Unit = {}

  override def scannerData(reqId: Int, rank: Int, contractDetails: ContractDetails, distance: String, benchmark: String,
    projection: String, legsStr: String
  ): Unit = {}

  override def displayGroupUpdated(reqId: Int, contractInfo: String): Unit = {}
}
