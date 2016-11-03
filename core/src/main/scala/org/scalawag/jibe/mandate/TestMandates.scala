package org.scalawag.jibe.mandate

import org.scalawag.jibe.multitree.{OnlyIdentityEquals, MandateExecutionContext, MandateHelpers, StatelessMandate}

case object NoisyMandate extends StatelessMandate {
  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    import context._

    log.debug("This is a debug message...")
    log.info("followed by an info message.")
    log.warn("Then, there's a warning.")
    log.error("Finally, an ERROR!!!!!")

    false
  }

  override def takeAction(implicit context: MandateExecutionContext) =
    try {
      throw new RuntimeException("BOOM")
    } catch {
      case ex: Exception =>
        throw new RuntimeException("message\nis\nlong", ex)
    }
}

case class ExitWithArgument(exitCode: Int) extends StatelessMandate with MandateHelpers with OnlyIdentityEquals {
  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    import context._
    val ec = runCommand(command.ExitWithArgument(exitCode))
    log.info(s"command exited with exit code: $ec")
    false
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    import context._
    val ec = runCommand(command.ExitWithArgument(-exitCode))
    log.warn(s"command exited with exit code: $ec")
  }
}
