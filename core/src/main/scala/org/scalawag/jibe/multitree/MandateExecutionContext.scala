package org.scalawag.jibe.multitree

import org.scalawag.jibe.backend.Commander
import org.scalawag.timber.api.Logger

// This will make it easier to add more capabilities to the execution context without having to rewrite all existing
// mandate code.

case class MandateExecutionContext(commander: Commander, log: Logger)
