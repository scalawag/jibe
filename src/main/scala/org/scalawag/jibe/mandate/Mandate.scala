package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.Resource
import org.scalawag.jibe.mandate.command.{BooleanCommand, Command, UnitCommand}

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

  // Some(true) -> action is not needed
  // Some(false) -> action is needed
  // None -> can't tell if action is needed
  def isActionCompleted(implicit context: MandateExecutionContext): Option[Boolean] = None

  // false -> action was unneeded
  // true -> action was needed and completed
  def takeActionIfNeeded(implicit context: MandateExecutionContext): Boolean

  protected[this] def ifNeeded(fn: => Unit)(implicit context: MandateExecutionContext): Boolean =
    isActionCompleted match {
      case Some(true) =>
        false
      case _ =>
        fn
        true
    }

  override def toString = description.getOrElse(super.toString)

  protected[this] def runCommand[A](command: Command[A])(implicit context: MandateExecutionContext) = {
    context.commander.execute(command)
  }
}

// TODO: lose this pimper?  It's only here to make this seem more DSL-y and this isn't the DSL.  We don't have one yet.
object Mandate {
  implicit class MandatePimper(mandate: Mandate) {
    def before(after: Mandate) = mandate match {
      // If the mandate we're adding to is already a fixed-order CompositeMandate, just add it.
      case CompositeMandate(desc, innards, true) => new CompositeMandate(desc, innards :+ after, true)
      case m => new CompositeMandate(None, Seq(m, after), true)
    }
  }
}
