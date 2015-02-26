package com.larroy.ibclient

/**
 * @author piotr 10.02.15
 * @param time seconds since epoch 1/1/1970 GMT.
 * @param count number of trades that occurred
 * @param wap is weighted average price during the time of the bar
 */
case class Bar(time: Long, high: Double, low: Double, open: Double, close: Double,  volume: Int, count: Int, wap: Double, hasGaps: Boolean)
