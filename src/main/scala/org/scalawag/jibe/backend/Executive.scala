package org.scalawag.jibe.backend

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.mandate.MandateResults.Outcome
import org.scalawag.jibe.mandate._
import scala.util.{Failure, Success, Try}

object Executive {
  def TAKE_ACTION = { (a: Mandate, c: MandateExecutionContext) => a.takeAction(c) }
  def IS_ACTION_COMPLETED = { (a: CheckableMandate, c: MandateExecutionContext) => a.isActionCompleted(c) }
  def TAKE_ACTION_IF_NEEDED = { (a: CheckableMandate, c: MandateExecutionContext) => a.takeActionIfNeeded(c) }

  val takeAction = executeMandate(TAKE_ACTION)_
  val isActionCompleted = executeMandate(IS_ACTION_COMPLETED)_
  val takeActionIfNeeded = executeMandate(TAKE_ACTION_IF_NEEDED)_

  def executeMandate[A <: Mandate, B](fn: (A, MandateExecutionContext) => B)(resultsDir: File, commander: Commander, mandate: A): B = {

    val startTime = System.currentTimeMillis
    // Execute the mandate and catch any exceptions
    val rv = Try(fn(mandate, MandateExecutionContext(commander, resultsDir)))
    val endTime = System.currentTimeMillis

    val outcome = rv match {
      case Success(false) => Outcome.USELESS // From isActionCompleted or takeActionIfNeeded
      case Success(true)  => Outcome.SUCCESS // Ditto
      case Success(_)     => Outcome.SUCCESS // TODO: more graceful way to handle return of takeAction (non-checkable)
      case Failure(ex)    => Outcome.FAILURE
    }

    // Write the stack trace (if there is one) to a file in the results directory

    rv recover { case ex =>
      writeFileWithPrintWriter(resultsDir / "stack-trace") { pw =>
        ex.printStackTrace(pw)
      }
    }

    // Write the summary of the mandate execution to a file in the results directory

    writeFileWithPrintWriter(resultsDir / "mandate.js") { pw =>
      import spray.json._
      import JsonFormat._
      val results = MandateResults(mandate.description, outcome, mandate.isInstanceOf[CompositeMandateBase[_]], startTime, endTime)
      pw.write(results.toJson.prettyPrint)
    }

    // Either return the mandate execution return or rethrow the exception

    rv.get
  }
}
