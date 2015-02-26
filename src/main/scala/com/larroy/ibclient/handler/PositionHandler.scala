package com.larroy.ibclient.handler

import com.larroy.ibclient.Position

import scala.collection.mutable

/**
 * @author piotr 20.02.15
 */
case class PositionHandler(queue: mutable.Queue[Position] = mutable.Queue.empty[Position]) extends Handler
