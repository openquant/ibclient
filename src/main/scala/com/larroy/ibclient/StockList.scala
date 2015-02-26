package com.larroy.ibclient

import java.io.InputStreamReader

import scala.util.Try

/**
 * @author piotr 10.01.15
 */
object StockList {
  object ExchangeType extends Enumeration {
    type ExchangeType = Value
    val NYSE = Value("NYSE")
    val AMEX = Value("AMEX")
    val NASDAQ = Value("NASDAQ")
  }
  import ExchangeType._

  case class StockInfo(ticker: String, name: String, capM: Option[BigDecimal], IPOyear: Option[Int], Sector: String, Industry: String, url: String)

  def listExchange(exchange: ExchangeType): Vector[StockInfo] = {
    val resPath: String = exchange match {
      case NYSE => "/nyse.csv"
      case AMEX => "/amex.csv"
      case NASDAQ => "/nasdaq.csv"
    }
    val is = getClass.getResourceAsStream(resPath)
    if (is == null)
      List.empty[StockInfo]
    import com.github.tototoshi.csv._
    val rdr = CSVReader.open(new InputStreamReader(is))
    rdr.toStream.drop(1).map(parseCSVLine).toVector
  }

  val capre = """\$(\d+(?:.\d+))([MB])""".r

  /// Capitalization from $13.B or $512.5M or n/a in Millions
  def getCapMill(x: String): Option[BigDecimal] = {
    x match {
      case capre(dollars, unit) => {
        unit match {
          case "B" =>
            Some(BigDecimal(dollars) * 1000)
          case "M" =>
            Some(BigDecimal(dollars))
          case _ =>
            None
        }
      }
      case _ => None
    }
  }

  def parseCSVLine(x: List[String]): StockInfo = {
    val IPOyear = Try(x(4).toInt).toOption
    new StockInfo(x(0), x(1), getCapMill(x(3)), IPOyear, x(5), x(6), x(7))
  }
}
