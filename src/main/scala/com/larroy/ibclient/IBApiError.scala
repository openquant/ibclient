package com.larroy.ibclient

/**
 * @author piotr 2/10/15
 */
case class IBApiError(msg: String) extends Exception(msg)
