package com.larroy.trabot.ib.handler

import com.ib.client.ContractDetails

import scala.collection.mutable

/**
 * @author piotr 20.02.15
 */
case class ContractDetailsHandler(
  details: mutable.ArrayBuffer[ContractDetails] = mutable.ArrayBuffer.empty[ContractDetails]
) extends Handler
