package org.scalawag.jibe.outputs

import scala.concurrent.ExecutionContext

class RunContext {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  val log = Logging.log
  //    var roots: MandateReport = ???
}
