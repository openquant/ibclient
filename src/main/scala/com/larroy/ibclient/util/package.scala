package com.larroy.ibclient

import java.util.Calendar

import org.joda.time.format.DateTimeFormat

/**
 * @author piotr 03.03.15
 */
package object util {
  private val dateTimeFormat = DateTimeFormat.forPattern("yyyyMMdd")
  def dateEpoch_s(date: String): Long = {
    if (date.length() == 8)
      dateTimeFormat.parseDateTime(date).toDate.getTime / 1000
    else
      date.toLong
  }
}
