package org.scalawag.jibe.mandate

import org.scalamock.scalatest.MockFactory
import org.scalawag.jibe.{Logging, TestLogging}
import org.scalawag.jibe.backend.Commander
import org.scalawag.jibe.multitree.MandateExecutionContext

trait MandateTest extends MockFactory {
  TestLogging

  val commander = mock[Commander]

  implicit val context = MandateExecutionContext(commander, Logging.log)

  def executing[A](cmd: command.Command[A]) =
    (commander.execute(_: command.Command[A])(_: MandateExecutionContext)).expects(cmd, context)
}
