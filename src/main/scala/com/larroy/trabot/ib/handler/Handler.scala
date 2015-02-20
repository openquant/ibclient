package com.larroy.trabot.ib.handler

/**
 * @author piotr 20.02.15
 */
trait Handler {
  def error(throwable: Throwable): Unit = {}
}

