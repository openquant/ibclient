package com.larroy.trabot.ib

import com.ib.client.TickType

/**
 * @author piotr 14.02.15
 */

object Tick {
  def apply(numericTickType: Int, value: Double): Tick = {
    val tickType = TickType.get(numericTickType)
    numericTickType match {
      case 0 => new TickBidSize(tickType, value)
      case 1 => new TickBid(tickType, value)
      case 2 => new TickAsk(tickType, value)
      case 3 => new TickAskSize(tickType, value)
      case 4 => new TickLast(tickType, value)
      case 5 => new TickLastSize(tickType, value)
      case 6 => new TickHigh(tickType, value)
      case 7 => new TickLow(tickType, value)
      case 8 => new TickVolume(tickType, value)
      case 9 => new TickClose(tickType, value)
      case 10 => new TickBidOption(tickType, value)
      case 11 => new TickAskOption(tickType, value)
      case 12 => new TickLastOption(tickType, value)
      case 13 => new TickModelOption(tickType, value)
      case 14 => new TickOpen(tickType, value)
      case 21 => new TickAvgVolume(tickType, value)
      case 22 => new TickOpenInterest(tickType, value)
      case 23 => new TickOptionHistoricalVol(tickType, value)
      case 24 => new TickOptionImpliedVol(tickType, value)
      case 25 => new TickOptionBidExch(tickType, value)
      case 26 => new TickOptionAskExch(tickType, value)
      case 27 => new TickOptionCallOpenInterest(tickType, value)
      case 28 => new TickOptionPutOpenInterest(tickType, value)
      case 29 => new TickOptionCallVolume(tickType, value)
      case 30 => new TickOptionPutVolume(tickType, value)
      case 31 => new TickIndexFuturePremium(tickType, value)
      case 32 => new TickBidExch(tickType, value)
      case 33 => new TickAskExch(tickType, value)
      case 34 => new TickAuctionVolume(tickType, value)
      case 35 => new TickAuctionPrice(tickType, value)
      case 36 => new TickAuctionImbalance(tickType, value)
      case 37 => new TickMarkPrice(tickType, value)
      case 38 => new TickBidEfpComputation(tickType, value)
      case 39 => new TickAskEfpComputation(tickType, value)
      case 40 => new TickLastEfpComputation(tickType, value)
      case 41 => new TickOpenEfpComputation(tickType, value)
      case 42 => new TickHighEfpComputation(tickType, value)
      case 43 => new TickLowEfpComputation(tickType, value)
      case 44 => new TickCloseEfpComputation(tickType, value)
      case 45 => new TickLastTimestamp(tickType, value)
      case 46 => new TickShortable(tickType, value)
      case 47 => new TickFundamentalRatios(tickType, value)
      case 48 => new TickRtVolume(tickType, value)
      case 49 => new TickHalted(tickType, value)
      case 50 => new TickBidYield(tickType, value)
      case 51 => new TickAskYield(tickType, value)
      case 52 => new TickLastYield(tickType, value)
      case 53 => new TickCustOptionComputation(tickType, value)
      case 54 => new TickTradeCount(tickType, value)
      case 57 => new TickLastRthTrade(tickType, value)
      case 58 => new TickRtHistoricalVol(tickType, value)
      case 61 => new TickRegulatoryImbalance(tickType, value)
      case _ => new TickUnknown(tickType, value)
    }
  }
}

class Tick(tickType: TickType, value: Double)

case class TickBidSize(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickBid(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAsk(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAskSize(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLast(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLastSize(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickHigh(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLow(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickVolume(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickClose(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickBidOption(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAskOption(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLastOption(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickModelOption(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOpen(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAvgVolume(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOpenInterest(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionHistoricalVol(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionImpliedVol(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionBidExch(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionAskExch(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionCallOpenInterest(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionPutOpenInterest(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionCallVolume(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOptionPutVolume(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickIndexFuturePremium(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickBidExch(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAskExch(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAuctionVolume(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAuctionPrice(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAuctionImbalance(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickMarkPrice(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickBidEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAskEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLastEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickOpenEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickHighEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLowEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickCloseEfpComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLastTimestamp(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickShortable(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickFundamentalRatios(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickRtVolume(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickHalted(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickBidYield(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickAskYield(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLastYield(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickCustOptionComputation(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickTradeCount(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickLastRthTrade(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickRtHistoricalVol(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickRegulatoryImbalance(tickType: TickType, value: Double) extends Tick(tickType, value)

case class TickUnknown(tickType: TickType, value: Double) extends Tick(tickType, value)
