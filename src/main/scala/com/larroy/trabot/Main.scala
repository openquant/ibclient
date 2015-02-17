package com.larroy.trabot

import java.text.SimpleDateFormat
import java.util.Date

import com.ib.client.Contract
import com.ib.client.Types.{WhatToShow, BarSize, DurationUnit, SecType}
import com.ib.contracts.StkContract
import com.larroy.trabot.ib.IBClient
import com.larroy.trabot.ib.contract.{FutureContract, StockContract}
import org.slf4j.{Logger, LoggerFactory}
import rx.schedulers.Schedulers

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object Mode extends Enumeration {
  type Mode = Value
  val Invalid, Test, Populate, History = Value
}

import Mode._

sealed case class Options(
  host: String = "localhost",
  port: Int = 7496,
  mode: Mode = Mode.Invalid,
  quiet: Boolean = false,
  contract: Option[String] = None,
  contractType: SecType = SecType.valueOf("STK"),
  contractExchange: String = "SMART",
  contractCurrency: String = "USD",
  contractExpiry: String = "",
  historyDuration: Int = 10,
  historyDurationUnit: DurationUnit = DurationUnit.DAY,
  historyBarSize: BarSize = BarSize._1_hour,
  historyEndDate: String = new SimpleDateFormat("yyyyMMdd hh:mm:ss").format(new Date())
)

/**
 * @author piotr 19.10.14
 */
object Main {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val version = "0.1"

  def getOptionParser: scopt.OptionParser[Options] = {
    val contractTypes = SecType.values().map(_.name)
    val durationUnits = DurationUnit.values().map(_.name)
    val barSizes = BarSize.values().map(_.name)
    new scopt.OptionParser[Options]("trabot") {
      head("trabot", Main.version)

      override def showUsageOnError: Boolean = true

      help("help") text ("print help")
      /*
       * Common arguments
       */
      opt[Boolean]('q', "quiet") text ("suppress progress on stdout") action {
        (arg, dest) => dest.copy(quiet = arg)
      }
      opt[String]('h', "host") text ("host") action {
        (arg, dest) => dest.copy(host = arg)
      }
      opt[Int]('p', "port") text ("port") action {
        (arg, dest) => dest.copy(port = arg)
      }
      cmd("test") text ("test") action {
        (_, dest) => dest.copy(mode = Mode.Test)
      } children(
        opt[String]('c', "contract") text ("contract") minOccurs (1) action {
          (arg, dest) => dest.copy(contract = Some(arg))
        },
        opt[String]('t', "type") text ("contract type") action {
          (arg, dest) => dest.copy(contractType = SecType.valueOf(arg))
        } validate (x => if (contractTypes.contains(x)) success else failure("unknown contract type")),
        note(s"contract type is one of: '${contractTypes.mkString(" ")}'"),

        opt[String]('e', "exchange") text ("exchange") action {
          (arg, dest) => dest.copy(contractExchange = arg)
        },
        opt[String]('s', "currency") text ("currency") action {
          (arg, dest) => dest.copy(contractCurrency = arg)
        },
        opt[Int]('d', "duration") text ("duration") action {
          (arg, dest) => dest.copy(historyDuration = arg)
        },
        opt[String]('u', "durationunits") text ("duration units") action {
          (arg, dest) => dest.copy(historyDurationUnit = DurationUnit.valueOf(arg))
        } validate (x => if (durationUnits.contains(x)) success else failure("unknown duration unit")),
        note(s"duration unit is one of: '${durationUnits.mkString(" ")}'"),

        opt[String]('b', "barsize") text ("bar size") action {
          (arg, dest) => dest.copy(historyBarSize = BarSize.valueOf(arg))
        } validate (x => if (barSizes.contains(x)) success else failure("unknown bar size")),
        note(s"duration unit is one of: '${barSizes.mkString(" ")}'"),

        opt[String]('e', "enddate") text ("end date") action {
          (arg, dest) => dest.copy(historyEndDate = arg)
        }
        )
      cmd("history") text ("history") action {
        (_, dest) => dest.copy(mode = Mode.History)
      } children(
        opt[String]('c', "contract") text ("contract") minOccurs (1) action {
          (arg, dest) => dest.copy(contract = Some(arg))
        },
        opt[String]('t', "type") text ("contract type") action {
          (arg, dest) => dest.copy(contractType = SecType.valueOf(arg))
        } validate (x => if (contractTypes.contains(x)) success else failure("unknown contract type")),
        note(s"contract type is one of: '${contractTypes.mkString(" ")}'"),

        opt[String]('e', "exchange") text ("exchange") action {
          (arg, dest) => dest.copy(contractExchange = arg)
        },
        opt[String]('s', "currency") text ("currency") action {
          (arg, dest) => dest.copy(contractCurrency = arg)
        },
        opt[String]('x', "expiry") text ("expiry") action {
          (arg, dest) => dest.copy(contractExpiry = arg)
        },
        opt[Int]('d', "duration") text ("duration") action {
          (arg, dest) => dest.copy(historyDuration = arg)
        },
        opt[String]('u', "durationunits") text ("duration units") action {
          (arg, dest) => dest.copy(historyDurationUnit = DurationUnit.valueOf(arg))
        } validate (x => if (durationUnits.contains(x)) success else failure("unknown duration unit")),
        note(s"duration unit is one of: '${durationUnits.mkString(" ")}'"),

        opt[String]('b', "barsize") text ("bar size") action {
          (arg, dest) => dest.copy(historyBarSize = BarSize.valueOf(arg))
        } validate (x => if (barSizes.contains(x)) success else failure("unknown bar size")),
        note(s"duration unit is one of: '${barSizes.mkString(" ")}'"),

        opt[String]('e', "enddate") text ("end date") action {
          (arg, dest) => dest.copy(historyEndDate = arg)
        }
        )
    }
  }

  def main(args: Array[String]) {
    val optionParser = getOptionParser
    val options: Options = optionParser.parse(args, Options()).getOrElse {
      log.error("Option syntax incorrect")
      log.error(s"Arguments given ${args.mkString("'", "' '", "'")}")
      log.error("Failure.")
      sys.exit(1)
    }

    val success: Boolean = try options.mode match {
      case Mode.Invalid => {
        optionParser.reportError("Please specify a valid command")
        optionParser.showUsage
        false
      }

      case Mode.Test => {
        test(options)
        true
      }
      case Mode.Populate => {
        populate(options)
        true
      }
      case Mode.History => {
        history(options)
        true
      }
    } catch {
      case e: Exception => {
        log.error(s"Exception thrown ${e.getMessage}")
        e.printStackTrace()
        false
      }
    }

    if (success) {
      log.info("Success.")
      log.info("=========== trabot finished successfully ================")
      sys.exit(0)
    } else {
      log.error("Failure.")
      log.info("=========== trabot finished with errors =================")
      sys.exit(-1)
    }
  }

  def test(options: Options): Unit = {
    val ibclient = new IBClient("localhost", 7496, 0)
    Await.result(ibclient.connect(), Duration.Inf)
    var s = ibclient.marketData(new StockContract("SPY", "SMART"))
    s.observableTick.subscribe(
      {tick ⇒ println(tick)},
      {error ⇒ throw(error)},
      {() ⇒ println("Closed")}
    )
    while (true)
      Thread.sleep(1000)


  }

  def populate(options: Options): Unit = {

  }

  def history(options: Options): Unit = {
    val ibclient = new IBClient("localhost", 7496, 3)
    Await.result(ibclient.connect(), Duration.Inf)
    val contract: Contract = options.contractType match {
      case SecType.STK => new StockContract(options.contract.get, options.contractExchange, options.contractCurrency)
      case SecType.FUT => new FutureContract(options.contract.get, options.contractExpiry, options.contractExchange,
        options.contractCurrency
      )
      case _ => throw new RuntimeException("contract type")
    }

    /*
    val futureContractDetails = ibclient.contractDetails(contract)
    val cd = Await.result(futureContractDetails, Duration.Inf)
    println(cd)
    */

    val res = ibclient.historicalData(contract, options.historyEndDate, options.historyDuration,
      options.historyDurationUnit, options.historyBarSize, WhatToShow.MIDPOINT, false
    )
    val hist = Await.result(res, Duration.Inf)
    ibclient.disconnect()
    println(hist)
  }
}
