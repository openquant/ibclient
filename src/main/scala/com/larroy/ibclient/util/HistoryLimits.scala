package com.larroy.ibclient.util

import java.text.SimpleDateFormat
import java.util.Date

import com.ib.client.Types.{DurationUnit, BarSize, WhatToShow}
import com.larroy.ibclient.IBClient
import com.larroy.ibclient.contract.StockContract
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import net.ceedubs.ficus.Ficus._

object HistoryLimits {
  private[this] val cfg = ConfigFactory.load().getConfig("ibclient.historyLimits")

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
  def bestDuration(startDate: Date, endDate: Date, barSize: BarSize): DurationUnit = {
    DurationUnit.DAY
  }
}

/**
 * @author piotr 01.03.15
 * History limits are not clearly documented, this class can automatically find the limits
 */
class HistoryLimits {
  private[this] val log: Logger = LoggerFactory.getLogger(this.getClass)
  private[this] val cfg = ConfigFactory.load().getConfig("ibclient.test")
  private[this] val ibclient = connectedClient
  private[this] val endDate = new Date()
  private[this] val contract = testStockContract

  def testWaitDuration = Duration(cfg.getInt("tws.timeout_s"), SECONDS)

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
    Await.ready(futureBars, Duration.Inf).value match {
      case Some(Failure(e)) ⇒ {
        log.debug(s"fail")
        false
      }
      case Some(Success(_)) ⇒ {
        log.debug(s"success")
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

  def calibrate(): Seq[ValidDurations] = {
    val barsizes = Array[BarSize](BarSize._15_mins, BarSize._1_hour, BarSize._4_hours, BarSize._1_day, BarSize._1_week)
    val durations = DurationUnit.values().drop(1)
    val limits = findLimits(barsizes, durations)
    limits
  }

}
