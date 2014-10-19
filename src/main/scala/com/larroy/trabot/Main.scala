package com.larroy.trabot

import org.slf4j.{Logger, LoggerFactory}


object Mode extends Enumeration {
  type Mode = Value
  val Invalid, Test  = Value
}
import Mode._

sealed case class Options(
  mode: Mode = Mode.Test,
  quiet: Boolean = false
)

/**
 * @author piotr 19.10.14
 */
object Main {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val version = "0.1"

  def main (args: Array[String]) {
    val optParser = new scopt.OptionParser[Options]("cdtfilter") {
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
}
