package com.larroy.trabot.ib

/**
 * @author piotr 10.02.15
 */
case class Bar(time: Long, high: Double, low: Double, open: Double, close: Double, wap: Double, volume: Int, count: Int, hasGaps: Boolean)
