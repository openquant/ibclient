package com.larroy.ibclient.util

import com.ib.client.Types.{DurationUnit, BarSize}

/**
 * @author piotr 01.03.15
 */
case class ValidDurations(barSize: BarSize, durationUnit: DurationUnit, durations: Array[Int])
