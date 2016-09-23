package org.scalawag.jibe.mandate

case class MandateResults(description: Option[String],
                          outcome: MandateResults.Outcome.Value,
                          composite: Boolean,
                          startTime: Long,
                          endTime: Long)
//{
//  val elapsedTime = endTime - startTime
//}

object MandateResults {
  object Outcome extends Enumeration {
    val SUCCESS = Value
    val FAILURE = Value
    val USELESS = Value
  }
}
