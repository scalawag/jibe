package org.scalawag.jibe.mandate

import java.io.File

import org.scalamock.scalatest.MockFactory
import org.scalawag.jibe.TestLogging
import org.scalawag.jibe.backend.{Commander, MandateExecutionContext}

trait MandateTest extends MockFactory {
  val log = TestLogging.log
  val commander = mock[Commander]
  val file: File = null // TODO:  I think this is unused and can be deleted now

  implicit val context = MandateExecutionContext(commander, file, log)

  def executing(cmd: command.BooleanCommand) =
    (commander.execute(_: command.BooleanCommand)(_: MandateExecutionContext)).expects(cmd, context)

  def executing(cmd: command.UnitCommand) =
    (commander.execute(_: command.UnitCommand)(_: MandateExecutionContext)).expects(cmd, context)
}
