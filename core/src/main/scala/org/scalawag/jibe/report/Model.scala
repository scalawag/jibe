package org.scalawag.jibe.report

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import spray.json._

object Model extends DefaultJsonProtocol {

  case class Run(version: Int, startTime: Date)
  //    extends Ordered[Run] {
  //    override def compare(that: Run) = this.startTime.getTime.compare(that.startTime.getTime)
  //  }

  val ISO8601DateFormat = {
    val f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    f.setTimeZone(TimeZone.getTimeZone("UTC"))
    f
  }

  implicit object dateFormat extends RootJsonFormat[Date] {
    def write(obj: Date): JsValue = JsString(ISO8601DateFormat.format(obj.getTime))
    def read(json: JsValue): Date = json match {
      case JsString(str) => ISO8601DateFormat.parse(str)
      case x => throw new RuntimeException(s"expecting a string in ISO8601 format: $x")
    }
  }

  implicit val runFormat = jsonFormat2(Run.apply)
}
