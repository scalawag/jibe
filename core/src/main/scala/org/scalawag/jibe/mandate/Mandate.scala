package org.scalawag.jibe.mandate

import org.scalawag.jibe.mandate.command.Command
import org.scalawag.jibe.multitree.MandateExecutionContext

/** A Mandate is an operation that can be executed against a system.  It may be realized as a series of system-specific
  * commands or it can be an aggregation of several other Mandates.
  *
  * Mandates should be idempotent.  In addition to being a convenience to the user that partial/failed runs can be
  * restarted without negative consequences, jibe also will optimize the run by avoiding running mandates that have
  * already been run (assuming that the outcome would be the same, anyway).
  */

trait Mandate {
  val description: Option[String] = None
  def prerequisites: Iterable[Resource] = Iterable.empty
  def consequences: Iterable[Resource] = Iterable.empty
}

trait StatelessMandate extends Mandate {
  def isActionCompleted(implicit context: MandateExecutionContext): Boolean = throw new UnsupportedOperationException
  def takeAction(implicit context: MandateExecutionContext): Unit
}

trait StatefulMandate[A] extends Mandate {
  def createState(implicit context: MandateExecutionContext): A
  def cleanupState(state: A)(implicit context: MandateExecutionContext): Unit = {}
  def isActionCompleted(state: A)(implicit context: MandateExecutionContext): Boolean
  def takeAction(state: A)(implicit context: MandateExecutionContext): Unit
}

trait MandateHelpers {
  protected[this] def runCommand[A](command: Command[A])(implicit context: MandateExecutionContext) = {
    context.commander.execute(command)
  }
}