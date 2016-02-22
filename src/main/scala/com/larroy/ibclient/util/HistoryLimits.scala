package com.larroy.ibclient.util

import java.text.SimpleDateFormat
import java.util.Date

import com.ib.client.Types.{DurationUnit, BarSize, WhatToShow}
import com.larroy.ibclient
import com.larroy.ibclient.{IBApiError, IBClient}
import com.larroy.ibclient.contract.StockContract
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import net.ceedubs.ficus.Ficus._

object HistoryLimits {
  private[this] val cfg = ConfigFactory.load()
    .withFallback(ConfigFactory.parseString(ibclient.defaultConfig))
    .getConfig("ibclient.historyLimits")

  /**
   * @param durationUnit
   * @param barSize
   * @return the maximum number of bar (duration) that can be requested with the
   *         given parameters or None if the combination is not valid.
   */
  def apply(durationUnit: DurationUnit, barSize: BarSize): Option[Int] = {
    val path = s"${durationUnit.name}.${barSize.name}"
    cfg.as[Option[Int]](path)
  }

  def bestDuration(startDate: Date, endDate: Date, barSize: BarSize): HistoryDuration = {
    import org.joda.time._
    val endDateTime = new DateTime(endDate)
    val startDateTime = new DateTime(startDate)
    val seconds =  Math.abs(Seconds.secondsBetween(startDateTime, endDateTime).getSeconds)

    val secondsInDay = 24 * 60 * 60
    val daysRem = if (seconds/secondsInDay > 0 && seconds % secondsInDay != 0) 1 else 0
    val days = seconds / secondsInDay + daysRem

    val secondsInWeek = 7 * secondsInDay
    val weeksRem = if (seconds / secondsInWeek > 0 && seconds % secondsInWeek != 0) 1 else 0
    val weeks = seconds / secondsInWeek + weeksRem

    val secondsInMonth = 30 * secondsInDay
    val monthsRem = if (seconds / secondsInMonth > 0 && seconds % secondsInMonth != 0) 1 else 0
    val months = seconds / secondsInMonth + monthsRem

    val secondsInYear = 365 * secondsInDay
    val yearsRem = if (seconds / secondsInYear > 0 && seconds % secondsInYear != 0) 1 else 0
    val years = seconds / secondsInYear

    if (years == 0 && months == 0 && weeks == 0 && days == 0 && barSize.ordinal > BarSize._1_hour.ordinal)
      new HistoryDuration(Math.max(days, 1), this(DurationUnit.DAY, barSize).getOrElse(1), DurationUnit.DAY)

    else if (years == 0 && months == 0 && weeks == 0 && days <= 1 && barSize.ordinal < BarSize._1_day.ordinal)
      new HistoryDuration(Math.max(seconds, 1), this(DurationUnit.SECOND, barSize).getOrElse(1), DurationUnit.SECOND)

    else if (years == 0 && months == 0 && weeks == 0 || barSize.ordinal <= BarSize._5_mins.ordinal)
      new HistoryDuration(Math.max(days, 1), this(DurationUnit.DAY, barSize).getOrElse(1), DurationUnit.DAY)

    else if (years == 0 && months == 0 || barSize.ordinal <= BarSize._15_mins.ordinal)
      new HistoryDuration(Math.max(weeks, 1), this(DurationUnit.WEEK, barSize).getOrElse(1), DurationUnit.WEEK)

    else if (years == 0 || barSize.ordinal <= BarSize._1_hour.ordinal)
      new HistoryDuration(Math.max(months, 1), this(DurationUnit.MONTH, barSize).getOrElse(1), DurationUnit.MONTH)

    else
      new HistoryDuration(Math.max(years, 1), this(DurationUnit.YEAR, barSize).getOrElse(1), DurationUnit.YEAR)
  }
}

/**
 * @author piotr 01.03.15
 * History limits are not clearly documented, this class can automatically find the limits via calibrate
 */
class HistoryLimits {
  import com.larroy.ibclient.defaultConfig
  private[this] val log: Logger = LoggerFactory.getLogger(this.getClass)
  private[this] val cfg = ConfigFactory.load()
    .withFallback(ConfigFactory.parseString(defaultConfig))
    .getConfig("ibclient")

  private[this] val ibclient = connectedClient
  private[this] val endDate = new Date()
  private[this] val contract = testStockContract

  def testWaitDuration = scala.concurrent.duration.Duration(cfg.getInt("tws.timeout_s"), SECONDS)

  def testStockContract: StockContract = {
    new StockContract(
      cfg.getString("params.stock.contract"),
      cfg.getString("params.stock.exchange"),
      cfg.getString("params.stock.currency")
    )
  }

  def connectedClient: IBClient = {
    val ibclient = new IBClient(cfg.getString("tws.host"), cfg.getInt("tws.port"), cfg.getInt("tws.clientId"))
    Await.result(ibclient.connect(), testWaitDuration)
    ibclient
  }

  def validDuration(barSize: BarSize, durationUnit: DurationUnit, duration: Int): Boolean = {
    log.debug(s"validDuration ${barSize} ${durationUnit} ${duration}")
    val futureBars = ibclient.historicalData(contract, endDate, duration,
      durationUnit, barSize, WhatToShow.TRADES, false
    )
    Thread.sleep(16000)
    Await.ready(futureBars, scala.concurrent.duration.Duration.Inf).value match {
      case Some(Failure(e)) ⇒ {
        log.debug(s"fail")
        false
      }
      case Some(Success(bars)) ⇒ {
        log.debug(s"success, ${bars.size} bars")
        true
      }
      case _ ⇒ {
        assert(false)
        false
      }
    }
  }

  def findLimits (barsizes: Array[BarSize], durations: Array[DurationUnit]): Seq[ValidDurations] =
  {
    implicit class Crossable[X](xs: Traversable[X]) {
      def cross[Y](ys: Traversable[Y]) = for {x <- xs; y <- ys} yield (x, y)
    }
    val candidates = barsizes.toVector cross durations.toVector
    def from(start: Int): Stream[Int] = Stream.cons(start, from(start + 1))
    val validDurations = candidates.map { x ⇒
      val barSize = x._1
      val durationUnit = x._2
      new ValidDurations(barSize, durationUnit, from(1).takeWhile(validDuration(barSize, durationUnit, _)).toArray)
    }.toSeq
    validDurations
  }

  /**
    * Finds the maximum set of [[ValidDurations]] for history requests
    * @return
    */
  def calibrate(): Seq[ValidDurations] = {
    val barsizes = Array[BarSize](BarSize._15_mins, BarSize._1_hour, BarSize._4_hours, BarSize._1_day, BarSize._1_week)
    val durations = DurationUnit.values().drop(1)
    val limits = findLimits(barsizes, durations)
    limits
  }

}
