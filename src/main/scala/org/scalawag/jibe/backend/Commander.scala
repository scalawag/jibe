package org.scalawag.jibe.backend

import org.scalawag.jibe.mandate.MandateExecutionContext
import org.scalawag.jibe.mandate.command.{BooleanCommand, Command, UnitCommand}

trait Commander {
  def execute(context: MandateExecutionContext, command: Command): Int

  def execute(context: MandateExecutionContext, command: BooleanCommand): Boolean =
    execute(context, command.asInstanceOf[Command]) match {
      case 0 => true
      case 1 => false
      case n => throw new RuntimeException(s"BooleanCommand exited with exit code $n")
    }

  def execute(context: MandateExecutionContext, command: UnitCommand): Unit =
    execute(context, command.asInstanceOf[Command]) match {
      case 0 => Unit
      case n => throw new RuntimeException(s"UnitCommand exited with error code $n")
    }
}
