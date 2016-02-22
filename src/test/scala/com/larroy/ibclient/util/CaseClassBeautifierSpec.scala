package com.larroy.ibclient.util

import org.specs2.mutable._

case class C(a: String, b: Int)

class CaseClassBeautifierSpec extends Specification {
  "CaseClassBeautifierSpec" should {
    "do" in {
      val c = C("h", 1)
      CaseClassBeautifier(c) should_==("C(a = h, b = 1)")
    }
  }
}