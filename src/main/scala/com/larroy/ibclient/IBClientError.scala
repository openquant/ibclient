package com.larroy.ibclient

/**
 * @author piotr 16.02.15
 */
case class IBClientError(msg: String) extends Exception(msg)
