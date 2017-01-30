package org.scalawag.jibe.outputs

abstract class SimpleLogicMandate[-A, +B] extends Mandate[A, B] { self =>
  protected[this] def dryRunLogic(in: A)(implicit runContext: RunContext): Option[B]
  protected[this] def runLogic(in: A)(implicit runContext: RunContext): B

  class SimpleLogicBoundMandate(upstream: UpstreamBoundMandate[A])(implicit runContext: RunContext) extends BoundMandate[B] {
    import runContext.executionContext

    override val upstreams: Iterable[UpstreamBoundMandate[_]] = Iterable(upstream)

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

    override val toString = self.toString
  }

  override def bind(upstream: UpstreamBoundMandate[A])(implicit runContext: RunContext): UpstreamBoundMandate[B] =
    new SimpleLogicBoundMandate(upstream)
}
