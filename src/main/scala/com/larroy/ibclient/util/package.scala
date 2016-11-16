package com.larroy.ibclient

import java.time.{LocalDate, ZoneId, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Calendar

import org.joda.time.format.DateTimeFormat

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author piotr 03.03.15
 */
package object util {
  //private val dateTimeFormat = DateTimeFormat.forPattern("yyyyMMdd")
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
  def dateEpoch_s(date: String): Long = {
    if (date.length() == 8) {
      // http://stackoverflow.com/questions/23596530/unable-to-obtain-zoneddatetime-from-temporalaccessor-using-datetimeformatter-and
      val zonedDateTime = LocalDate.parse(date, dateFormatter)
      val x = zonedDateTime.atStartOfDay(ZoneOffset.UTC)
      x.toEpochSecond
      //dateTimeFormat.parseDateTime(date).toDate.getTime / 1000
    } else
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

   def retry[T](n: Int)(block: => T, delay_ms: Long = 1000, base: Long = 2): T = {
    try {
      block
    } catch {
      case e if n > 1 => {
        val log: Logger = LoggerFactory.getLogger(this.getClass)
        log.debug(s"retry block: suspending thread for ${delay_ms} ms")
        log.debug(s"\texception was: ${e.toString}")
        Thread.sleep(delay_ms)
        retry(n - 1)(block, delay_ms * base, base)
      }
    }
  }

  def retryWhen[T](n: Int)(block: => T, when: (Throwable) ⇒ Boolean, delay_ms: Long = 1000, base: Long = 2): T = {
    try {
      block
    } catch {
      case e if n > 1 && when(e) ⇒ {
        val log: Logger = LoggerFactory.getLogger(this.getClass)
        log.debug(s"retry block: suspending thread for ${delay_ms} ms")
        log.debug(s"\texception was: ${e.toString}")
        Thread.sleep(delay_ms)
        retry(n - 1)(block, delay_ms * base, base)
      }
    }
  }



  /*
  @annotation.tailrec
  def retry[T](n: Int)(fn: => T): Try[T] = {
    Try { fn } match {
      case x: Success[T] => x
      case Failure(NonFatal(_)) if n > 1 => retry(n - 1)(fn)
      case f => f
    }
  }
  */
}
