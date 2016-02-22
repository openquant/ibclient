package com.larroy.ibclient.util

import java.util.Date

import com.ib.client.Types.{DurationUnit, BarSize}

/**
 * @author piotr 01.03.15
 */
case class HistoricalRequest(contract: String, exchange: String, endDate: Date, duration: Int, durationUnit: DurationUnit, barSize: BarSize) extends Ordered[HistoricalRequest] {
  //import scala.math.Ordered.orderingToOrdered
  def compare(that: HistoricalRequest): Int = {
    //(this.contract, this.exchange, this.durationUnit, this.barSize, this.duration) compare (that.contract, that.exchange, that.durationUnit, that.barSize, that.duration)
    Ordering[(String, String, Date, Int, DurationUnit, BarSize)].compare(
      (this.contract, this.exchange, this.endDate, this.duration, this.durationUnit, this.barSize),
      (that.contract, that.exchange, that.endDate, that.duration, that.durationUnit, that.barSize)
    )
  }





}

