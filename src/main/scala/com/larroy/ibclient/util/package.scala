package com.larroy.ibclient

import java.util.Calendar

import org.joda.time.format.DateTimeFormat

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author piotr 03.03.15
 */
package object util {
  private val dateTimeFormat = DateTimeFormat.forPattern("yyyyMMdd")
  def dateEpoch_s(date: String): Long = {
    if (date.length() == 8)
      dateTimeFormat.parseDateTime(date).toDate.getTime / 1000
    else
      date.toLong
  }

  def divRoundAwayZero(x: Long, div: Long) = (x + (Math.abs(div) - 1)) / div
  def divRoundAwayZero(x: Int, div: Int) = (x + (Math.abs(div) - 1)) / div

  def defer[A](interval_ms: Long)(block: ⇒ A)(implicit ctx: ExecutionContext): Future[A] = {
    Future {
      Thread.sleep(interval_ms)
      block
    }(ctx)
  }
}
