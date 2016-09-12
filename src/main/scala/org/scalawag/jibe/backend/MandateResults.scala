package org.scalawag.jibe.backend

object MandateResults {
  object Outcome extends Enumeration {
    val SUCCESS = Value
    val FAILURE = Value
    val USELESS = Value
  }
}

case class MandateResults(mandate: Mandate,
                          outcome: MandateResults.Outcome.Value,
                          innards: Either[Iterable[MandateResults], CommandResults])
