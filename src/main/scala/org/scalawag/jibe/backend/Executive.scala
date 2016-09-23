package org.scalawag.jibe.backend

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.mandate.MandateResults.Outcome
import org.scalawag.jibe.mandate._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object Executive {

  def takeAction(resultsDir: File, targets: Map[Commander, Mandate]): Unit =
    execute(resultsDir, targets)(_.takeAction(_, _))

  def isActionCompleted(resultsDir: File, targets: Map[Commander, CheckableMandate]): Unit =
    execute(resultsDir, targets)(_.isActionCompleted(_, _))

  def takeActionIfNeeded(resultsDir: File, targets: Map[Commander, CheckableMandate]): Unit =
    execute(resultsDir, targets)(_.takeActionIfNeeded(_, _))

  private[this] def execute[A <: Mandate, B](resultsDir: File, targets: Map[Commander, A])(fn: (A, Commander, File) => B): Unit = {
    val futures = targets map { case (commander, mandate) =>
      Future(executeMandate(fn)(resultsDir / commander.toString, commander, mandate))
    }

    Await.ready(Future.sequence(futures), Duration.Inf) // TODO: eventually go all asynchronous?
  }

  def TAKE_ACTION = { (a: Mandate, c: Commander, dir: File) => a.takeAction(c, dir) }
  def IS_ACTION_COMPLETED = { (a: CheckableMandate, c: Commander, dir: File) => a.isActionCompleted(c, dir) }
  def TAKE_ACTION_IF_NEEDED = { (a: CheckableMandate, c: Commander, dir: File) => a.takeActionIfNeeded(c, dir) }

  def executeMandate[A <: Mandate, B](fn: (A, Commander, File) => B)(resultsDir: File, commander: Commander, mandate: A): B = {

    val startTime = System.currentTimeMillis
    // Execute the mandate and catch any exceptions
    val rv = Try(fn(mandate, commander, resultsDir))
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
