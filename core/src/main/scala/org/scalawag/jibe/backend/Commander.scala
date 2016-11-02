package org.scalawag.jibe.backend

import org.scalawag.jibe.mandate.command.{BooleanCommand, Command, IntCommand, UnitCommand}
import org.scalawag.jibe.multitree.MandateExecutionContext

import scala.util.{Failure, Success, Try}

trait Commander {
  def execute[A](command: Command[A])(implicit context: MandateExecutionContext): A

  def executeBooleanScript(script: String, description: String = "")(implicit context: MandateExecutionContext): Boolean

  def executeIntScript(script: String, description: String = "")(implicit context: MandateExecutionContext): Int

  def execute(script: String, description: String = "")(implicit context: MandateExecutionContext): Unit

  // Generic handler for interpreting the exit code of a command execution.  The interpreter must be specified by the
  // caller.  This is intended to factor out common logic from the other protected interpretExitCode methods.  It's
  // not designed to be used directly.

  private[this] def interpretExitCode[A](description: String, fn: => Int)
                                        (interpreter: Int => (String, Try[A]))
                                        (implicit context: MandateExecutionContext): A =
  {
    context.log.info(MandateExecutionLogging.CommandStart)(description)

    // Do the command work (specified by the caller)
    val ec = fn

    // Convert the exit code to the appropriate type

    val (exitCodeDescription, answer) = interpreter(ec)

    context.log.info(MandateExecutionLogging.CommandExit)(exitCodeDescription)

    answer.get
  }

  protected def interpretExitCodeInt(description: String)(fn: => Int)(implicit context: MandateExecutionContext): Int =
    interpretExitCode[Int](description, fn) { ec =>
      (ec.toString, Success(ec))
    }

  protected def interpretExitCodeBoolean(description: String)(fn: => Int)(implicit context: MandateExecutionContext): Boolean =
    interpretExitCode[Boolean](description, fn) { ec =>
      ec match {
        case 0 => ((s"$ec (true)"), Success(true))
        case 1 => ((s"$ec (false)"), Success(false))
        case n => ((s"$ec (ERROR)"), Failure(new RuntimeException(s"BooleanCommand exited with exit code $n")))
      }
    }

  protected def interpretExitCodeUnit(description: String)(fn: => Int)(implicit context: MandateExecutionContext): Unit =
    interpretExitCode[Unit](description, fn) { ec =>
      ec match {
        case 0 => ((s"$ec (success)"), Success(()))
        case n => ((s"$ec (ERROR)"), Failure(new RuntimeException(s"UnitCommand exited with error code $n")))
      }
    }

  // Interprets the exit code based on the type of Command passed in.

  protected def interpretExitCodeCommand[A](command: Command[A])(fn: => Int)(implicit context: MandateExecutionContext): A =
    command match {
      case _: BooleanCommand => interpretExitCodeBoolean(command.toString)(fn).asInstanceOf[A]
      case _: UnitCommand    => interpretExitCodeUnit(command.toString)(fn).asInstanceOf[A]
      case _: IntCommand     => interpretExitCodeInt(command.toString)(fn).asInstanceOf[A]
    }
}
