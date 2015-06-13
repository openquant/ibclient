package com.larroy.ibclient.order

/**
 * Created by piotr on 6/13/15.
 */
object ExecutionStatus extends Enumeration {
  type ExecutionStatus = Value
  val
    PendingSubmit,
    PendingCancel,
    PreSubmitted,
    Submitted,
    Cancelled,
    Filled,
    Inactive = Value
}
