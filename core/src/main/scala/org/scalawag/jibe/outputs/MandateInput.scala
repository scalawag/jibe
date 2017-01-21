package org.scalawag.jibe.outputs

import scala.concurrent.{ExecutionContext, Future}

trait MandateInput[+A] { me =>
  def dryRunResults: Future[DryRun.Result[A]]
  def runResults: Future[Run.Result[A]]

  def map[B](fn: A => B)(implicit runContext: RunContext, executionContext: ExecutionContext): Mandate[B] =
    new Mandate[B] {
      override protected[this] def dryRun()(implicit runContext: RunContext) =
        me.dryRunResults map { drr =>
          drr.map(fn)
        }

      override protected[this] def run()(implicit runContext: RunContext) =
        me.runResults map { rr =>
          rr.map(fn)
        }
    }

  /* TODO: consider taking this out if it's not useful
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

  def flatMap[B](you: Mandate[B])(implicit runContext: RunContext, executionContext: ExecutionContext): Mandate[B] =
    new Mandate[B] {
      override protected[this] def dryRun()(implicit runContext: RunContext) = {
        import DryRun._
        me.dryRunResults flatMap {
          case Unneeded(r) => you.dryRunResults
          case Needed => Future.successful(Needed)
          case Blocked => Future.successful(Blocked)
        }
      }

      override protected[this] def run()(implicit runContext: RunContext) = {
        import Run._
        me.runResults flatMap {
          case Unneeded(mr) => you.runResults
          case Done(mr) =>
            // We need to return done if this returned Done to indicate that an action was taken.
            you.runResults map {
              case Done(yr) => Done(yr)
              case Unneeded(yr) => Done(yr)
              case Blocked => Blocked
            }
          case Blocked => Future.successful(Blocked)
        }
      }
    }

  def flatMap[C >: A, B](you: OpenMandate[C, B])(implicit runContext: RunContext): Mandate[B] = you.bind(me)

  def join[C >: A, B](you: Mandate[B])(implicit runContext: RunContext, executionContext: ExecutionContext): Mandate[(C,B)] =
    new Mandate[(C, B)] {
      override def dryRun()(implicit runContext: RunContext): Future[DryRun.Result[(A, B)]] = {
        import DryRun._
        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val lf = me.dryRunResults
        val rf = you.dryRunResults
        lf flatMap {
          case Unneeded(lo) =>
            rf map {
              case Unneeded(ro) => Unneeded(lo, ro)
              case Blocked => Blocked
              case Needed => Needed
            }
          case Blocked => Future.successful(Blocked)
          case Needed => Future.successful(Needed)
        }
      }

      override def run()(implicit runContext: RunContext) = {
        import Run._
        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val lf = me.runResults
        val rf = you.runResults
        lf flatMap {
          case Unneeded(lo) =>
            rf map {
              case Unneeded(ro) => Unneeded(lo, ro)
              case Done(ro) => Done(lo, ro)
              case Blocked => Blocked
            }
          case Done(lo) =>
            rf map {
              case Unneeded(ro) => Done(lo, ro)
              case Done(ro) => Done(lo, ro)
              case Blocked => Blocked
            }
          case Blocked => Future.successful(Blocked)
        }
      }
    }

}

object MandateInput {
  implicit def fromLiteral[A](a: A): MandateInput[A] = new MandateInput[A] {
    override val dryRunResults = Future.successful(DryRun.Unneeded(a))
    override val runResults = Future.successful(Run.Unneeded(a))
  }
}

