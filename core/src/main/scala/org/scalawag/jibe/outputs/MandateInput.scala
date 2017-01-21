package org.scalawag.jibe.outputs

import scala.concurrent.Future

/** An abstraction for the inputs required by a mandate which may come from static code or may come from the output
  * of another mandate.
  *
  * @tparam A the output type
  */

trait MandateInput[+A] { me =>
  def dryRunResult: Future[DryRun.Result[A]]
  def runResult: Future[Run.Result[A]]

  /** Produces a new MandateInput which returns the same dry-run and run results as this mandate with the output
    * (if present) transformed using the provided function. For results that have no output, the function is not used.
    */

  def map[B](fn: A => B)(implicit runContext: RunContext): Mandate[B] =
    new Mandate[B] {
      import runContext.executionContext

      override protected[this] def dryRun()(implicit runContext: RunContext) =
        me.dryRunResult map { drr =>
          drr.map(fn)
        }

      override protected[this] def run()(implicit runContext: RunContext) =
        me.runResult map { rr =>
          rr.map(fn)
        }
    }

/* TODO: Consider uncommenting this if it proves useful, otherwise, delete it.
    def flatMap[B](fn: A => Future[B]): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults flatMap {
            case Unneeded(r) => fn(r).map(Unneeded.apply)
            case Needed => Future.successful(Needed)
            case Blocked => Future.successful(Blocked)
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults flatMap {
            case Unneeded(r) => fn(r).map(Unneeded.apply)
            case Done(r) => fn(r).map(Done.apply)
            case Blocked => Future.successful(Blocked)
          }
      }
*/

  /** Produces a new MandateInput which calculates the results of this input and then the results of the provided
    * mandate, ignoring the output of this one.  If this input returns an incomplete result (Blocked or Needed), then
    * that result is used as the result of the new input and the provided mandate's result is not calculated.
    */

  def flatMap[B](you: MandateInput[B])(implicit runContext: RunContext): Mandate[B] =
    new Mandate[B] {
      import runContext.executionContext

      override protected[this] def dryRun()(implicit runContext: RunContext) = {
        import DryRun._
        me.dryRunResult flatMap {
          case Unneeded(r) => you.dryRunResult
          case Needed => Future.successful(Needed)
          case Blocked => Future.successful(Blocked)
        }
      }

      override protected[this] def run()(implicit runContext: RunContext) = {
        import Run._
        me.runResult flatMap {
          case Unneeded(mr) => you.runResult
          case Done(mr) =>
            // We need to return done if this returned Done to indicate that an action was taken.
            you.runResult map {
              case Done(yr) => Done(yr)
              case Unneeded(yr) => Done(yr)
              case Blocked => Blocked
            }
          case Blocked => Future.successful(Blocked)
        }
      }
    }

  /** Produces a new MandateInput which calculates the results of this input and then, if it is complete, binds the
    * output to the provided open mandate, which provides the output of the returned input.  If this input returns an
    * incomplete result (Blocked or Needed), then that result is used as the result of the new input and the
    * provided mandate's result is not calculated.
    */

  def flatMap[C >: A, B](you: OpenMandate[C, B])(implicit runContext: RunContext): Mandate[B] = you.bind(me)

  /** Produces a new MandateInput which calculates the results of this input and the provided input in parallel and
    * then returns the results of both combined in a tuple.  If either input returns an incomplete result (Blocked
    * or Needed), then the result of the new input is the same.  If either input returns a Done result, the new input
    * returns a Done.  Otherwise, when both inputs return Unneeded results, the result of the new input is Unneeded.
    */

  def join[C >: A, B](you: MandateInput[B])(implicit runContext: RunContext): Mandate[(C,B)] =
    new Mandate[(C, B)] {
      import runContext.executionContext

      override def dryRun()(implicit runContext: RunContext): Future[DryRun.Result[(A, B)]] = {
        import DryRun._

        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val lf = me.dryRunResult
        val rf = you.dryRunResult

        lf flatMap { lr =>
          rf map { rr =>
            (lr, rr) match {
              case (Unneeded(lo), Unneeded(ro)) => Unneeded(lo, ro)
              case (Blocked, _) => Blocked
              case (_, Blocked) => Blocked
              case (Needed, _) => Needed
              case (_, Needed) => Needed
            }
          }
        } recover {
          case _ => Blocked
        }
      }

      override def run()(implicit runContext: RunContext) = {
        import Run._
        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val lf = me.runResult
        val rf = you.runResult

        lf flatMap { lr =>
          rf map { rr =>
            (lr, rr) match {
              case (Unneeded(lo), Unneeded(ro)) => Unneeded(lo, ro)
              case (lc: Completed[C], rc: Completed[B]) => Done(lc.output, rc.output)
              case (Blocked, _) => Blocked
              case (_, Blocked) => Blocked
            }
          }
        } recover {
          case _ => Blocked
        }
      }
    }

}

object MandateInput {
  implicit def fromLiteral[A](a: A): MandateInput[A] = new MandateInput[A] {
    override val dryRunResult = Future.successful(DryRun.Unneeded(a))
    override val runResult = Future.successful(Run.Unneeded(a))
  }
}
