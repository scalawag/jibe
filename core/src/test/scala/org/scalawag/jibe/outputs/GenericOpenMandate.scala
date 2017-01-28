package org.scalawag.jibe.outputs
/*
import scala.concurrent.duration._

class GenericOpenMandate[I, O](dryRunLogicFn: I => Option[O], runLogicFn: I => O,
                               dryRunDelay: FiniteDuration = 0 seconds, runDelay: FiniteDuration = 0 seconds)
  extends Mandate[I, O]
{
  var dryRunStart: Option[Long] = None
  var dryRunFinish: Option[Long] = None
  var runStart: Option[Long] = None
  var runFinish: Option[Long] = None

  override val upstream = Set.empty

  class GenericMandate(upstream: UpstreamBoundMandate[I])(implicit override val runContext: RunContext)
    extends SimpleLogicMandate[I, O](upstream)(runContext)
  {
    override val toString: String = s"GenericMandate"

    override protected[this] def dryRunLogic(in: I)(implicit runContext: RunContext) = {
      dryRunStart = Some(System.currentTimeMillis)
      try {
        if (dryRunDelay > 0.milliseconds)
          Thread.sleep(dryRunDelay.toMillis)
        dryRunLogicFn(in)
      } finally {
        dryRunFinish = Some(System.currentTimeMillis)
      }
    }

    override protected[this] def runLogic(in: I)(implicit runContext: RunContext) = {
      runStart = Some(System.currentTimeMillis)
      try {
        if ( runDelay > 0.milliseconds )
          Thread.sleep(runDelay.toMillis)
        runLogicFn(in)
      } finally {
        runFinish = Some(System.currentTimeMillis)
      }
    }
  }

  override def bind(in: UpstreamBoundMandate[I])(implicit runContext: RunContext) = new GenericMandate(in)
}
*/