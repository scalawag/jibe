package org.scalawag.jibe.backend

import org.scalawag.jibe.mandate.MandateResults
import org.scalawag.jibe.mandate.MandateResults.Outcome
import spray.json._

object JsonFormat extends DefaultJsonProtocol {

  implicit object outcomeFormat extends RootJsonFormat[Outcome.Value] {
    def write(obj: Outcome.Value): JsValue = JsString(obj.toString)
    def read(json: JsValue): Outcome.Value = json match {
      case JsString(str) => Outcome.withName(str)
      case x => throw new RuntimeException(s"unknown enumeration value: $x")
    }
  }

  implicit val resultsFormat = jsonFormat5(MandateResults.apply)
}
