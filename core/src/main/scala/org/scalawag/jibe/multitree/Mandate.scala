package org.scalawag.jibe.multitree

import org.scalawag.jibe.MD5
import org.scalawag.jibe.mandate.command.Command

/** A Mandate is an operation that can be executed against a system.  It may be realized as a series of system-specific
  * commands or it can be an aggregation of several other Mandates.
  *
  * Mandates should be idempotent.  In addition to being a convenience to the user that partial/failed runs can be
  * restarted without negative consequences, jibe also will optimize the run by avoiding running mandates that have
  * already been run (assuming that the outcome would be the same, anyway).
  */

trait Mandate {

  // Should be the same across runs for any mandate with the same behavior.  Right now, this uses class name and
  // hashCode to mean that a mandate with the same class and the same arguments is the same mandate.  The hash code
  // works this way because all the current mandates are case classes.  It has to be overridden to provide similar
  // functionality for non-case classes.

  val mandateFingerprint = MD5(s"${getClass.getName}:${hashCode}")
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

  protected[this] def caseClassFingerprint = Some
}

trait OnlyIdentityEquals {
  override def equals(any: Any) = any match {
    case that: AnyRef => this eq that
    case _ => false
  }
}
