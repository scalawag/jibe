package org.scalawag.jibe.mandate

case object NoisyMandate extends CheckableMandate {
  override val description = Some("Make a lot of noise.")

  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    log.debug("This is a debug message...")
    log.info("followed by an info message.")
    log.warn("Then, there's a warning.")
    log.error("Finally, an ERROR!!!!!")

    false
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    throw new RuntimeException("BOOM")
  }
}

case class ExitWithArgument(exitCode: Int) extends CheckableMandate {
  override val description = Some(s"exit with $exitCode")

  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    log.info("command exited with exit code: " + runCommand("isActionCompleted", command.ExitWithArgument(exitCode)))
    false
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    log.warn("command exited with exit code: " + runCommand("isActionCompleted", command.ExitWithArgument(-exitCode)))
  }
}