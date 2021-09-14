package com.larroy.ibclient.order

import com.ib.client.{Order ⇒ IBOrder}
import com.larroy.ibclient.order.kind.Kind

/**
 * @author piotr 19.02.15
 */
trait Order {
  def kind: Kind = ???
  def quantity: Int = ???
  def account: Option[String] = ???
  def toIBOrder: IBOrder = {
    import com.larroy.ibclient.order.kind._
    val iBOrder = new IBOrder()
    this match {
      case Buy(_, qty, acct) ⇒ {
        iBOrder.action("BUY")
        iBOrder.totalQuantity(qty)
        acct.collect {
          case acctStr => iBOrder.account(acctStr)
        }
      }
      case Sell(_, qty, acct) ⇒ {
        iBOrder.action("SELL")
        iBOrder.totalQuantity(qty)
        acct.collect {
          case acctStr => iBOrder.account(acctStr)
        }
      }
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
