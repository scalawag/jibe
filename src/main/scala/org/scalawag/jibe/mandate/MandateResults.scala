package org.scalawag.jibe.mandate

case class MandateResults(mandate: Mandate,
                          outcome: MandateResults.Outcome.Value,
                          startTime: Long,
                          endTime: Long,
                          innards: Iterable[MandateResults] = Iterable.empty)

object MandateResults {
  object Outcome extends Enumeration {
    val SUCCESS = Value
    val FAILURE = Value
    val USELESS = Value
  }
}




