package org.scalawag.jibe.backend

import java.io.File
import org.scalawag.jibe.mandate.command.{UnitCommand, BooleanCommand, Command}

trait Commander {
  def execute(resultsDir: File, command: Command): Int

  def execute(resultsDir: File, command: BooleanCommand): Boolean =
    execute(resultsDir, command.asInstanceOf[Command]) match {
      case 0 => true
      case 1 => false
      case n => throw new RuntimeException(s"command exited with error code $n")
    }

  def execute(resultsDir: File, command: UnitCommand): Unit =
    execute(resultsDir, command.asInstanceOf[Command]) match {
      case 0 => Unit
      case n => throw new RuntimeException(s"command exited with error code $n")
    }
}
