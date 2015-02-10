package com.larroy.trabot.ib

import java.util

import com.ib.client.Types._
import com.ib.client.{EClientSocket, EWrapper, ContractDetails, Contract}

import org.slf4j.{Logger, LoggerFactory}
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.{Promise, Future}

object APIState extends Enumeration {
  type APIState = Value
  val WaitForConnection, Connected, Disconnected= Value
}
//import APIState._


case class HistoricalDataHandler(queue: mutable.Queue[Bar] = mutable.Queue.empty[Bar], promise: Promise[IndexedSeq[Bar]] = Promise[IndexedSeq[Bar]]())

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

  override def historicalData(
    reqId: Int,
    date: String,
    open: Double, high: Double, low: Double, close: Double,
    volume: Int,
    count: Int,
    WAP: Double,
    hasGaps: Boolean
  ): Unit = {

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

  case class BarGap(bar: Bar, hasGaps: Boolean)

  def historicalData(contract: Contract, endDate: String, duration: Int, durationUnit: DurationUnit, barSize: BarSize, whatToShow: WhatToShow, rthOnly: Boolean): Future[IndexedSeq[Bar]] = {
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
}
