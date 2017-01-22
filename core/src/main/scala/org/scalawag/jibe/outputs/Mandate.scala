package org.scalawag.jibe.outputs

import scala.concurrent.Future

// Mandate input should be defined when it's created so that it's output can only be calculated once without fear that
// the input will change.  If a mandate allows multiple calls (with different arguments), then its output can't be
// cached as the canonical output of that mandate.

abstract class Mandate[A](implicit runContext: RunContext) extends MandateInput[A] { me =>
  import runContext.executionContext

  protected[this] def dryRun()(implicit runContext: RunContext): Future[DryRun.Result[A]]
  protected[this] def run()(implicit runContext: RunContext): Future[Run.Result[A]]

  lazy val dryRunResult: Future[DryRun.Result[A]] = dryRun()

  lazy val runResult: Future[Run.Result[A]] =
    dryRunResult flatMap {
      case DryRun.Needed => run()
      case DryRun.Unneeded(x) => Future.successful(Run.Unneeded(x))
      case DryRun.Blocked => Future.successful(Run.Blocked)
    }
}
