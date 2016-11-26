package org.scalawag.jibe.multitree

import org.scalawag.jibe.MD5
import org.scalawag.jibe.mandate.command.Command

import scala.Product1

/** A Mandate is an operation that can be executed against a system.  It may be realized as a series of system-specific
  * commands or it can be an aggregation of several other Mandates.
  *
  * Mandates should be idempotent.  In addition to being a convenience to the user that partial/failed runs can be
  * restarted without negative consequences, jibe also will optimize the run by avoiding running mandates that have
  * already been run (assuming that the outcome would be the same, anyway).
  */

trait Mandate {
  // A short-ish way to represent this mandate in the UI and logs.
  val label: String

  // Decorations that make sense with this mandate by default.
  val decorations: Set[MultiTreeDecoration] = Set.empty

  // The fingerprint should be the same across runs for two mandates with the same behavior.  Normally, that means
  // two instances of the same Mandate subclass that have the same arguments. This can be achieved by making your
  // Mandate subclass a case class and using the hashCode (which is derived from the constructor arguments) and the
  // class name as the fingerprint. If the Madate subclass is not a case class, this behavior needs to be ensured
  // another way.

  val fingerprint: String
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

trait CaseClassMandate extends Mandate {
  override val label: String = this.toString
  override val fingerprint: String = MD5(s"${getClass.getName}:${hashCode}")
}

trait OnlyIdentityEquals {
  override def equals(any: Any) = any match {
    case that: AnyRef => this eq that
    case _ => false
  }
}
