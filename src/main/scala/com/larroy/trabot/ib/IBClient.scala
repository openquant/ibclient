package com.larroy.trabot.ib

import java.util

import com.ib.controller.{ApiConnection, ApiController}

import org.slf4j.{Logger, LoggerFactory}

/**
 * @author piotr 19.10.14
 */
class IBClient(val host: String, val port: Int, val clientId: Int) extends ApiController.IConnectionHandler with ApiConnection.ILogger {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  var isConnected: Boolean = false
  val apiController = new ApiController(this, this, this)
  connect()

  def connect(): Unit = {
    log.info(s"Connecting to $host $port (clientId $clientId)")
    apiController.connect(host, port, clientId)
    while (! isConnected) {
      log.info("Waiting for connection")
      wait(1000)
    }
    log.info("Connected")
  }

  override def log(x: String): Unit = {
    log.info(x)
  }

  override def connected(): Unit = {
    log.info(s"connected handler")
    isConnected = true
  }

  override def disconnected(): Unit = {
    log.info(s"disconnected handler")
    isConnected = false
    connect()
  }

  override def error(e: Exception): Unit = {
    log.error(s"error handler: ${e.getMessage}")
  }

  override def message(id: Int, errorCode: Int, errorMsg: String): Unit = {
    log.info(s"message handler id: $id errorCode: $errorCode errorMsg: $errorMsg")
  }

  override def show(string: String): Unit = {
    log.info(s"show handler $string")
  }

  override def accountList(list: util.ArrayList[String]): Unit = {
    log.info(s"accountList handler")
  }
}
