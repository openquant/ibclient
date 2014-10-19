package com.larroy.trabot

import com.ib.contracts.StkContract
import com.larroy.trabot.ib.IBClient
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object Mode extends Enumeration {
  type Mode = Value
  val Invalid, Test  = Value
}
import Mode._

sealed case class Options(
  mode: Mode = Mode.Invalid,
  quiet: Boolean = false
)

/**
 * @author piotr 19.10.14
 */
object Main {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val version = "0.1"

  def getOptionParser: scopt.OptionParser[Options] = {
    new scopt.OptionParser[Options]("cdtfilter") {
      head("trabot", Main.version)

      override def showUsageOnError: Boolean = true

      help("help") text ("print help")
      /*
       * Common arguments
       */
      opt[Boolean]('q', "quiet") text ("suppress progress on stdout") action {
        (arg, dest) => dest.copy(quiet = arg)
      }
      cmd("test") text ("test") action {
        (_, dest) => dest.copy(mode = Mode.Test)
      }
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
        test()
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

  def test(): Unit = {
    val ibclient = new IBClient("localhost", 7496, 2)
    val futureContractDetails = ibclient.contractDetails(new StkContract("MSFT"))
    val cd = Await.result(futureContractDetails, Duration.Inf)
    println(cd)
    ibclient.disconnect()
  }
}
