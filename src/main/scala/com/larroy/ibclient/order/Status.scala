package com.larroy.ibclient.order

/**
 * Created by piotr on 6/13/15.
 */
object Status extends Enumeration {
  type Status = Value
  val
    PendingSubmit,
    PendingCancel,
    PreSubmitted,
    Submitted,
    Cancelled,
    Filled,
    Inactive = Value
}
