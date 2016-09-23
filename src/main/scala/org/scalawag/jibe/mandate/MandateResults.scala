package org.scalawag.jibe.mandate

import java.io.File

import org.scalawag.jibe.backend.Commander

case class MandateResults(description: Option[String],
                          outcome: MandateResults.Outcome.Value,
                          composite: Boolean,
                          startTime: Long,
                          endTime: Long)

object MandateResults {
  object Outcome extends Enumeration {
    val SUCCESS = Value
    val FAILURE = Value
    val USELESS = Value
  }
}

// This will make it easier to add more capabilities to the execution context without having to rewrite all existing
// mandate code.

case class MandateExecutionContext(commander: Commander, resultsDir: File)