package org.scalawag.jibe.backend

import java.io.{File, PrintWriter}
import org.scalawag.jibe.AbortException
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.mandate.MandateResults.Outcome
import org.scalawag.jibe.mandate._
import scala.util.{Failure, Success, Try}

object Executive {
  private[jibe] val IS_ACTION_COMPLETED = { (a: Mandate, c: MandateExecutionContext) => a.isActionCompleted(c) }
  private[jibe] val TAKE_ACTION_IF_NEEDED = { (a: Mandate, c: MandateExecutionContext) => a.takeActionIfNeeded(c) }

  def isActionCompleted(resultsDir: File, commander: Commander, mandates: Mandate*) =
    executeRootMandates(IS_ACTION_COMPLETED)(resultsDir, commander, mandates:_*)
  def takeActionIfNeeded(resultsDir: File, commander: Commander, mandates: Mandate*) =
    executeRootMandates(TAKE_ACTION_IF_NEEDED)(resultsDir, commander, mandates:_*)

  def executeRootMandates[A](fn: (Mandate, MandateExecutionContext) => A)(resultsDir: File, commander: Commander, mandates: Mandate*): A =
    // Create an unnamed CompositeMandate to contain all of the mandates passed in.  This will act as the proxy for
    // the target, in terms of outcome, timestamps, etc.
    Executive.executeMandate(fn)(resultsDir / commander.toString, commander, new CompositeMandate(None, mandates))

  private[jibe] def executeMandate[A](fn: (Mandate, MandateExecutionContext) => A)(resultsDir: File, commander: Commander, mandate: Mandate): A = {

    val mec = MandateExecutionContext(commander, resultsDir, MandateExecutionLogging.createMandateLogger(resultsDir))

    val startTime = System.currentTimeMillis
    // Execute the mandate and catch any exceptions
    val rv = Try(fn(mandate, mec))
    val endTime = System.currentTimeMillis

    val outcome = rv match {
      case Success(false) => Outcome.USELESS // From isActionCompleted or takeActionIfNeeded
      case Success(true)  => Outcome.SUCCESS // Ditto
      case Success(_)     => Outcome.SUCCESS // TODO: more graceful way to handle return of takeAction (non-checkable)
      case Failure(ex)    => Outcome.FAILURE
    }

    // Write the summary of the mandate execution to a file in the results directory

    writeFileWithPrintWriter(resultsDir / "mandate.js") { pw =>
      import spray.json._
      import JsonFormat._
      val results = MandateResults(mandate.description, outcome, mandate.isInstanceOf[CompositeMandate], startTime, endTime)
      pw.write(results.toJson.prettyPrint)
    }

    // Write the stack trace (if there is one) to a file in the results directory

    rv recover {
      case abortion: AbortException =>
        // Don't write this to a stack-trace file.  It's already been done where the exception was thrown.
        // Rethrow so that mandate execution will cease.
      case ex =>
        mec.log.error(MandateExecutionLogging.ExceptionStackTrace) { pw: PrintWriter =>
          ex.printStackTrace(pw)
        }
    }

    // Either return the mandate execution return or rethrow the exception

    rv.getOrElse(throw new AbortException)
  }
}
