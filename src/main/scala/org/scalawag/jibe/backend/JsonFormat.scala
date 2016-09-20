package org.scalawag.jibe.backend

import spray.json._
import org.scalawag.jibe.mandate.MandateResults.Outcome

object JsonFormat extends spray.json.DefaultJsonProtocol {

  // These are versions of the internal classes that can be serialized to JSON.

  case class ShallowMandateResults(description: Option[String],
                                   composite: Boolean,
                                   outcome: Outcome.Value,
                                   startTime: Long,
                                   endTime: Long)
  {
    def elapsedTime = endTime - startTime
  }

  case class PersistentTarget(hostname: String,
                              username: String,
                              port: Int,
                              commander: String,
                              sudo: Boolean)

  object PersistentTarget {
    def apply(target: Target) = new PersistentTarget(target.hostname, target.username, target.port, target.commander.getClass.getSimpleName, target.sudo)
  }

  implicit object outcomeFormat extends RootJsonFormat[Outcome.Value] {
    def write(obj: Outcome.Value): JsValue = JsString(obj.toString)
    def read(json: JsValue): Outcome.Value = json match {
      case JsString(str) => Outcome.withName(str)
      case x => throw new RuntimeException(s"unknown enumeration value: $x")
    }
  }

  implicit val resultsFormat = jsonFormat5(ShallowMandateResults.apply)
  implicit val targetFormat = jsonFormat5(PersistentTarget.apply)
}
