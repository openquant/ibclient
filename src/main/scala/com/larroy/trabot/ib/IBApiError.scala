package com.larroy.trabot.ib

/**
 * @author piotr 2/10/15
 */
case class IBApiError(msg: String) extends Exception(msg)
