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

/**
 * @author piotr 19.10.14
 */
class IBClient(val host: String, val port: Int, val clientId: Int) extends EWrapper {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  val eclientSocket = new EClientSocket(this)
  val reqId: Int = 0

  val reqMap = mutable.Map.empty[]

  def connect(): Unit = {
    eclientSocket.eConnect(host, port, clientId)
  }

  override def nextValidId(orderId: Int): Unit = {
		orderId = orderId
		reqId = orderId + 10000000
    log.info("nextValidId")
	}

  override def error(e: Exception): Unit = {
    log.error(s"error handler: ${e.getMessage}")
  }

	override def error(e: Exception): Unit = {
    log.error(s"error handler: ${e.getMessage}")
    log.error(s"${e.getStackTrace}")
	}

	override def error(id: Int, errorCode: Int, errorMsg: String): Unit = {

  }

  override def message(id: Int, errorCode: Int, errorMsg: String): Unit = {
    log.info(s"message handler id: $id errorCode: $errorCode errorMsg: $errorMsg")
    if (errorCode == 162)
      historicalDataResult.failure(new IBApiError(s"code: ${errorCode} msg: ${errorMsg}"))
  }

  override def show(string: String): Unit = {
    log.info(s"show handler $string")
  }

  override def accountList(list: util.ArrayList[String]): Unit = {
    log.info(s"accountList handler")
  }


  /*** IB API ***/
  def connect(): Unit = {
    log.info(s"Connecting to $host $port (clientId $clientId)")
    apiState.synchronized {
      apiController.connect(host, port, clientId)
      while (apiState == APIState.WaitForConnection) {
        log.info("Waiting for connection")
        apiState.wait(1000)
      }
      apiState match {
        case APIState.Connected =>
          log.info("Connected")
        case APIState.Disconnected =>
          log.info("Connection failed")
      }
    }
  }

  def disconnect(): Unit = {
    apiController.disconnect()
  }

  def contractDetails(contract: Contract): Future[Seq[ContractDetails]] = {
    val result = Promise[Seq[ContractDetails]]()
    apiController.reqContractDetails(contract, new IContractDetailsHandler {
      override def contractDetails(list: util.ArrayList[ContractDetails]): Unit = {
        result.success(list.toVector)
      }
    })
    result.future
  }

  def fundamentals(contract: Contract, typ: FundamentalType): Future[String] = {
    fundamentalsResult = Promise[String]()
    apiController.reqFundamentals(contract, typ, new IFundamentalsHandler {
      override def fundamentals(x: String): Unit = {
        fundamentalsResult.success(x)
      }
    })
    fundamentalsResult.future
  }

  case class BarGap(bar: Bar, hasGaps: Boolean)

  def historicalData(contract: Contract, endDate: String, duration: Int, durationUnit: DurationUnit, barSize: BarSize, whatToShow: WhatToShow, rthOnly: Boolean): Future[IndexedSeq[BarGap]] = {
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
  }
}
