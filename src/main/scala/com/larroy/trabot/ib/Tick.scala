package com.larroy.trabot.ib

import com.ib.client.TickType

/**
 * @author piotr 14.02.15
 */
case class Tick(tickType: TickType, value: Double)
