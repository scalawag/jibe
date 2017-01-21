package org.scalawag.jibe.outputs

abstract class SimpleLogicMandate[MI, MO](upstream: MandateInput[MI])(implicit runContext: RunContext) extends Mandate[MO] {
  import runContext.executionContext

  protected[this] def dryRunLogic(in: MI)(implicit runContext: RunContext): Option[MO]
  protected[this] def runLogic(in: MI)(implicit runContext: RunContext): MO

  override protected[this] def dryRun()(implicit runContext: RunContext) = {
    import DryRun._
    upstream.dryRunResult map { drr =>
      drr flatMap { i =>
        dryRunLogic(i).map(Unneeded.apply).getOrElse(Needed)
      }
    }
  }

  override protected[this] def run()(implicit runContext: RunContext) = {
    import Run._
    upstream.runResult map { rr =>
      rr flatMap { i =>
        Done(runLogic(i))
      }
    }
  }
}
