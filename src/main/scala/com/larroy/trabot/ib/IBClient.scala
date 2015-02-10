package com.larroy.trabot.ib

import java.util
import java.util.{Calendar, Date}

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


case class HistoricalDataHandler(queue: mutable.Queue[Bar] = mutable.Queue.empty[Bar],
  promise: Promise[IndexedSeq[Bar]] = Promise[IndexedSeq[Bar]]()
)

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
  val eclientSocket = new EClientSocket(this)
  var reqId: Int = 0
  var orderId: Int = 0

  /**
   * A map of request id to Promise
   */
  val reqPromise = mutable.Map.empty[Int, AnyRef]

  def connect(): Unit = {
    eclientSocket.eConnect(host, port, clientId)
  }

  def disconnect(): Unit = {
    eclientSocket.eDisconnect()
  }

  override def nextValidId(id: Int): Unit = {
    orderId = id
    reqId = orderId + 10000000
    log.info("nextValidId")
  }

  override def error(e: Exception): Unit = {
    log.error(s"error handler: ${e.getMessage}")
    log.error(s"${e.getStackTrace}")
  }

  override def error(id: Int, errorCode: Int, errorMsg: String): Unit = {
    reqPromise.get(id).foreach { x =>
      val promise = x.asInstanceOf[Promise[_]]
      promise.failure(new IBApiError(s"code: ${errorCode} msg: ${errorMsg}"))
    }
  }

  def contractDetails(contract: Contract): Future[Seq[ContractDetails]] = {
    reqId += 1
    val promise = Promise[Seq[ContractDetails]]()
    reqPromise += (reqId -> promise)
    eclientSocket.reqContractDetails(reqId, contract)
    promise.future
  }

  override def contractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {
    log.debug("contractDetails")

  }

  override def contractDetailsEnd(reqId: Int): Unit = {
    log.debug("contractDetailsEnd")

  }


  def fundamentals(contract: Contract, typ: FundamentalType): Future[String] = {
    reqId += 1
    val promise = Promise[String]()
    reqPromise += (reqId -> promise)
    eclientSocket.reqFundamentalData(reqId, contract, typ.getApiString)
    promise.future
  }


  override def fundamentalData(reqId: Int, data: String): Unit = {
    reqPromise.get(reqId).foreach { x =>
      val promise = x.asInstanceOf[Promise[String]]
      promise.success(data)
    }
  }


  def historicalData(contract: Contract, endDate: String, duration: Int,
    durationUnit: DurationUnit, barSize: BarSize, whatToShow: WhatToShow, rthOnly: Boolean
  ): Future[IndexedSeq[Bar]] = {
    reqId += 1
    val historicalDataHandler = new HistoricalDataHandler()
    val promise = historicalDataHandler.promise
    reqPromise += (reqId -> historicalDataHandler)
    promise.future
    /*
    historicalDataResult = Promise[IndexedSeq[BarGap]]()
    apiController.reqHistoricalData(contract, endDate, duration, durationUnit, barSize, whatToShow, rthOnly, new IHistoricalDataHandler {
      val queue = mutable.Queue[BarGap]()
      override def historicalDataEnd(): Unit = {
        log.debug("historicalDataEnd")
        historicalDataResult.success(queue.toIndexedSeq)
      }
      override def historicalData(bar: Bar, hasGaps: Boolean): Unit ={
        log.debug(s"historicalData ${bar.toString}")
        queue += new BarGap(bar, hasGaps)
      }
    })
    historicalDataResult.future
    */
  }

  override def historicalData(
    reqId: Int,
    date: String,
    open: Double, high: Double, low: Double, close: Double,
    volume: Int,
    count: Int,
    wap: Double,
    hasGaps: Boolean
  ): Unit = {
    reqPromise.get(reqId).foreach { x =>
      val handler = x.asInstanceOf[HistoricalDataHandler]
      if (date.startsWith("finished")) {
        handler.promise.success(handler.queue.toIndexedSeq)
        reqPromise.remove(reqId)
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

  override def error(str: String): Unit = {}

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
