package org.scalawag.jibe.report

import spray.json.{JsString, JsValue, RootJsonFormat}
import spray.json._
import DefaultJsonProtocol._

object JsonFormat {
  class EnumerationFormat[A](enum: Enumeration) extends RootJsonFormat[A] {
    def write(obj: A): JsValue = JsString(obj.toString)
    def read(json: JsValue): A = json match {
      case JsString(str) => enum.withName(str).asInstanceOf[A]
      case x => throw new RuntimeException(s"unknown enumeration value: $x")
    }
  }

  implicit object mosFormat extends EnumerationFormat[ExecutiveStatus.Value](ExecutiveStatus)
  implicit val mandateStatusFormat = jsonFormat9(MandateStatus.apply)
}
