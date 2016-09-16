package org.scalawag.jibe.backend

import org.scalawag.jibe.mandate.MandateResults.Outcome

case class ShallowMandateResults(description: Option[String],
                                 composite: Boolean,
                                 outcome: Outcome.Value,
                                 startTime: Long,
                                 endTime: Long) {
                                   def elapsedTime = endTime - startTime
                                 }

object ShallowMandateResults {
  object JSON extends spray.json.DefaultJsonProtocol {
    import spray.json._

    implicit object outcomeFormat extends RootJsonFormat[Outcome.Value] {
      def write(obj: Outcome.Value): JsValue = JsString(obj.toString)
      def read(json: JsValue): Outcome.Value = json match {
        case JsString(str) => Outcome.withName(str)
        case x => throw new RuntimeException(s"unknown enumeration value: $x")
      }
    }

    implicit val resultsFormat = jsonFormat5(ShallowMandateResults.apply)
  }
}