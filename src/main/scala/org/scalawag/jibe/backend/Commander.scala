package org.scalawag.jibe.backend

import org.scalawag.jibe.mandate.MandateExecutionContext
import org.scalawag.jibe.mandate.command.{BooleanCommand, Command, UnitCommand}

import scala.util.{Failure, Success}

trait Commander {
  def execute[A](command: Command[A])(implicit context: MandateExecutionContext): A

  protected def process[A](command: Command[A])(fn: => Int)(implicit context: MandateExecutionContext): A = {
    context.log.info(MandateExecutionLogging.CommandStart)(command.toString)

    // Do the command work (specified by the caller)
    val ec = fn

    // Convert the exit code to the appropriate type

    val (exitCodeDescription, answer) =
      command match {
        case _: BooleanCommand =>
          ec match {
            case 0 => ((s"$ec (true)"), Success(true))
            case 1 => ((s"$ec (false)"), Success(false))
            case n => ((s"$ec (ERROR)"), Failure(new RuntimeException(s"BooleanCommand exited with exit code $n")))
          }

        case _: UnitCommand =>
          ec match {
            case 0 => ((s"$ec (success)"), Success(()))
            case n => ((s"$ec (ERROR)"), Failure(new RuntimeException(s"UnitCommand exited with error code $n")))
          }

        case _ =>
          (ec.toString, Success(ec))
      }

    context.log.info(MandateExecutionLogging.CommandExit)(exitCodeDescription)

    answer.get.asInstanceOf[A]
  }
}
