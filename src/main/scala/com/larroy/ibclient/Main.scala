package com.larroy.ibclient

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import com.github.tototoshi.csv.CSVWriter
import com.ib.client.Contract
import com.ib.client.Types.{WhatToShow, BarSize, DurationUnit, SecType}
import com.larroy.ibclient.contract.{CashContract, FutureContract, StockContract}
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import org.slf4j.{Logger, LoggerFactory}
//import rx.schedulers.Schedulers

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object Mode extends Enumeration {
  type Mode = Value
  val Invalid, History = Value
}

import Mode._

sealed case class Options(
  host: String = "localhost",
  port: Int = 7496,
  clientId: Int = 1,
  mode: Mode = Mode.Invalid,
  quiet: Boolean = false,
  contract: String = "",
  localSymbol: Option[String] = None,
  contractType: SecType = SecType.valueOf("STK"),
  contractExchange: String = "SMART",
  contractCurrency: String = "USD",
  contractExpiry: String = "",
  historyDurationUnit: DurationUnit = DurationUnit.DAY,
  historyBarSize: BarSize = BarSize._1_min,
  historyStartDate: Date = new DateTime(DateTimeZone.UTC).minusDays(1).toDate,
  historyEndDate: Date = new DateTime(DateTimeZone.UTC).toDate,
  historyOutFile: String = "history.csv"
)

//historyEndDate: Option[String] = Some(DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss z").print(new DateTime(DateTimeZone.UTC)))
/**
 * @author piotr 19.10.14
 */
object Main {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val version = "0.1"
  val dateTimeFormat = DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss z")

  def getOptionParser: scopt.OptionParser[Options] = {
    val contractTypes = SecType.values().map(_.name)
    val durationUnits = DurationUnit.values().map(_.name)
    val barSizes = BarSize.values().map(_.name)
    val validDateRe = """(\d{8}) (\d{2}:\d{2}:\d{2}) ?(\w*)?""".r
    new scopt.OptionParser[Options]("ibclient") {
      head("ibclient", Main.version)

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
      opt[Int]('i', "clientid") text ("client id") action {
        (arg, dest) => dest.copy(clientId = arg)
      }
      cmd("history") text ("history") action {
        (_, dest) => dest.copy(mode = Mode.History)
      } children(
        opt[String]('c', "contract") text ("contract") minOccurs (1) action {
          (arg, dest) => dest.copy(contract = arg)
        },
        opt[String]('s', "localsymbol") text ("localsymbol eg EUR.USD") action {
          (arg, dest) => dest.copy(localSymbol = Some(arg))
        },
        opt[String]('t', "type") text ("contract type") action {
          (arg, dest) => dest.copy(contractType = SecType.valueOf(arg))
        } validate (x => if (contractTypes.contains(x)) success else failure("unknown contract type")),
        note(s"contract type is one of: '${contractTypes.mkString(" ")}'"),

        opt[String]('e', "exchange") text ("exchange") action {
          (arg, dest) => dest.copy(contractExchange = arg)
        },
        opt[String]('x', "currency") text ("currency") action {
          (arg, dest) => dest.copy(contractCurrency = arg)
        },
        opt[String]('a', "startdate") text ("startdate") action {
          (arg, dest) => dest.copy(historyStartDate = dateTimeFormat.parseDateTime(arg).toDate)
        } validate {x ⇒
          x match {
            case validDateRe(_*) ⇒ success
            case _ ⇒ failure(s"argument doesn't match ${validDateRe.toString}")
          }
        },
        opt[String]('z', "enddate") text ("enddate") action {
          (arg, dest) => dest.copy(historyEndDate = dateTimeFormat.parseDateTime(arg).toDate)
        } validate {x ⇒
          x match {
            case validDateRe(_*) ⇒ success
            case _ ⇒ failure(s"argument doesn't match ${validDateRe.toString}")
          }
        },
        opt[String]('u', "durationunits") text ("duration units") action {
          (arg, dest) => dest.copy(historyDurationUnit = DurationUnit.valueOf(arg))
        } validate (x => if (durationUnits.contains(x)) success else failure("unknown duration unit")),
        note(s"duration unit is one of: '${durationUnits.mkString(" ")}'"),

        opt[String]('b', "barsize") text ("bar size") action {
          (arg, dest) => dest.copy(historyBarSize = BarSize.valueOf(arg))
        } validate (x => if (barSizes.contains(x)) success else failure("unknown bar size")),
        note(s"duration unit is one of: '${barSizes.mkString(" ")}'"),

        opt[String]('o', "out") text ("output file") action {
          (arg, dest) => dest.copy(historyOutFile = arg)
        },
        note(s"")
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
      log.info("=========== finished successfully ================")
      sys.exit(0)
    } else {
      log.error("Failure.")
      log.info("=========== finished with errors =================")
      sys.exit(-1)
    }
  }

  def history(options: Options): Unit = {
    val outFile = new File(options.historyOutFile)
    require(! outFile.exists(), s"Output file ${outFile.getAbsolutePath} already exists")

    val ibclient = new IBClient(options.host, options.port, options.clientId)
    log.info(s"Connecting to ${options.host}:${options.port} with client id: ${options.clientId}")
    Await.result(ibclient.connect(), Duration.Inf)
    val contract: Contract = options.contractType match {
      case SecType.STK => new StockContract(options.contract, options.contractExchange, options.contractCurrency)
      case SecType.FUT => new FutureContract(options.contract, options.contractExpiry, options.contractExchange,
        options.contractCurrency
      )
      case SecType.CASH ⇒ new CashContract(options.contract, options.localSymbol.get, options.contractExchange, options.contractCurrency)
      case _ => throw new RuntimeException(s"unsupported contract type ${options.contractType}")
    }

    /*
    val futureContractDetails = ibclient.contractDetails(contract)
    val cd = Await.result(futureContractDetails, Duration.Inf)
    println(cd)
    */
    import scala.concurrent.ExecutionContext.Implicits.global
    val res = ibclient.easyHistoricalData(contract, options.historyStartDate, options.historyEndDate, options.historyBarSize,
      WhatToShow.TRADES, false
    )
    val hist = Await.result(res, Duration.Inf)
    val csvWriter = CSVWriter.open(outFile)
    hist.foreach { bar ⇒
      csvWriter.writeRow(List(bar.time, bar.high, bar.low, bar.open, bar.close, bar.volume, bar.count, bar.hasGaps))
    }
    log.info(s"wrote ${hist.size} rows to ${outFile}")
    ibclient.disconnect()
  }
}
