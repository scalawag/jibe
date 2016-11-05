package org.scalawag.jibe.mandate

import org.scalamock.scalatest.MockFactory
import org.scalawag.jibe.TestLogging
import org.scalawag.jibe.backend.Commander
import org.scalawag.jibe.multitree.MandateExecutionContext

trait MandateTest extends MockFactory {
  val log = TestLogging.log
  val commander = mock[Commander]

  implicit val context = MandateExecutionContext(commander, log)

  def executing[A](cmd: command.Command[A]) =
    (commander.execute(_: command.Command[A])(_: MandateExecutionContext)).expects(cmd, context)
}
