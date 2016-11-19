package org.scalawag.jibe.report

import org.scalawag.jibe.multitree.MultiTreeId
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}
import Report._

object JsonFormats extends DefaultJsonProtocol {
  // These convert the above to JSON.

  implicit object StatusFormat extends RootJsonFormat[Status] {
    def write(obj: Status): JsValue = JsString(obj.toString)
    def read(json: JsValue): Status = json match {
      case JsString(str) => Status.withName(str)
      case x => throw new IllegalArgumentException(s"unsupported type for status: $json")
    }
  }

  implicit object MultiTreeIdFormat extends RootJsonFormat[MultiTreeId] {
    def write(obj: MultiTreeId): JsValue = JsString(obj.toString)
    def read(json: JsValue): MultiTreeId = json match {
      case JsString(str) => MultiTreeId(str)
      case x => throw new IllegalArgumentException(s"unsupported type for MultiTreeId: $json")
    }
  }

  implicit val ReportStatusFormat = jsonFormat4(ReportStatus.apply)
  implicit val RunReportAttributesFormat = jsonFormat6(RunReportAttributes.apply)
  implicit val CommanderReportAttributesFormat = jsonFormat2(CommanderReportAttributes.apply)
  implicit val BranchReportAttributesFormat = jsonFormat4(BranchReportAttributes.apply)
  implicit val LeafReportAttributesFormat = jsonFormat4(LeafReportAttributes.apply)
}
