package com.larroy.trabot.ib.order

import com.larroy.trabot.ib.order.kind.Kind

import com.ib.client.{Order ⇒ IBOrder}

/**
 * @author piotr 19.02.15
 */
trait Order {
  def kind: Kind = ???
  def toIBOrder: IBOrder = {
    import com.larroy.trabot.ib.order.kind._
    val iBOrder = new IBOrder()
    this match {
      case Buy(_) ⇒ iBOrder.action("BUY")
      case Sell(_) ⇒ iBOrder.action("SELL")
    }
    this.kind match {
      case Limit(limit) ⇒ {
        iBOrder.orderType("LMT")
        iBOrder.lmtPrice(limit)
        iBOrder.auxPrice(0)
      }
      case Market() ⇒ {
        iBOrder.orderType("MKT")
      }
      case Stop(stop) ⇒ {
        iBOrder.orderType("STP")
        iBOrder.lmtPrice(0)
        iBOrder.auxPrice(stop)
      }
      case StopLimit(stop, limit) ⇒ {
        iBOrder.orderType("STPLMT")
        iBOrder.lmtPrice(limit)
        iBOrder.auxPrice(stop)
      }
      case TrailStop(stop) ⇒ {
        iBOrder.orderType("TRAIL")
        iBOrder.lmtPrice(0)
        iBOrder.auxPrice(0)
      }
      case TrailStopLimit(stop, trail) ⇒ {
        iBOrder.orderType("TRAIL LIMIT")
        iBOrder.lmtPrice(0)
        iBOrder.auxPrice(stop)
        iBOrder.trailStopPrice(trail)
      }
      case TrailLimitIfTouched(stop, limit, trail) ⇒ {
        iBOrder.orderType("TRAIL LIT")
        iBOrder.trailStopPrice(trail)
        iBOrder.lmtPrice(limit)
        iBOrder.auxPrice(stop)
      }
      case TrailMarketIfTouched(stop, trail) ⇒ {
        iBOrder.orderType("TRAIL MIT")
        iBOrder.trailStopPrice(trail)
        iBOrder.auxPrice(stop)
        iBOrder.lmtPrice(0)
      }
    }
    iBOrder
  }
}
