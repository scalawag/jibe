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

  // Unit -> action was successfully taken
  // throw -> error
  def takeAction(implicit context: MandateExecutionContext): Unit

  override def toString = description.getOrElse(super.toString)

  protected[this] def runCommand(label: String, command: Command)(implicit context: MandateExecutionContext) = {
    context.commander.execute(context, command)
  }

  protected[this] def runCommand(label: String, command: BooleanCommand)(implicit context: MandateExecutionContext) = {
    context.commander.execute(context, command)
  }

  protected[this] def runCommand(label: String, command: UnitCommand)(implicit context: MandateExecutionContext) = {
    context.commander.execute(context, command)
  }

}

trait CheckableMandate extends Mandate {
  // true -> action is not needed
  // false -> action is needed
  def isActionCompleted(implicit context: MandateExecutionContext): Boolean

  // false -> action was unneeded
  // true -> action was needed and completed
  def takeActionIfNeeded(implicit context: MandateExecutionContext): Boolean =
    if ( isActionCompleted(context) ) {
      false
    } else {
      takeAction(context)
      true
    }
}

object Mandate {
  implicit class MandatePimper(mandate: Mandate) {
    def before(after: Mandate) = mandate match {
      // If the mandate we're adding to is already a fixed-order CompositeMandate, just add it.
      case CompositeMandate(desc, innards, true) => new CompositeMandate(desc, innards :+ after, true)
      case m => new CompositeMandate(None, Seq(m, after), true)
    }
  }
}
