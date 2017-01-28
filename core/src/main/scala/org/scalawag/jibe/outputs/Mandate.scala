package org.scalawag.jibe.outputs

import java.io.PrintWriter

import scala.concurrent.Future

trait Mandate[-A, +B] { me =>
  /** Binds this mandate to a given input, producing a mandate which is ready to be executed. */
  def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext): UpstreamBoundMandate[B]

  def map[C](fn: B => C): Mandate[A, C] =
    new Mandate[A, C] {
      override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) =
        new BoundMandate[C] {
          import runContext.executionContext

          private[this] val upstream = me.bind(in)

          override val upstreams = List(upstream)

          override val toString: String = s"map fn"

          override protected[this] def dryRun()(implicit runContext: RunContext) =
            upstream.dryRunResult map { drr =>
              drr.map(fn)
            }

          override protected[this] def run()(implicit runContext: RunContext) =
            upstream.runResult map { rr =>
              rr.map(fn)
            }
        }
    }

  def flatMap[C](fn: B => Mandate[B, C]): Mandate[A, C] =
    new Mandate[A, C] {
      override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext): UpstreamBoundMandate[C] =
        new BoundMandate[C] {
          import runContext.executionContext

          private[this] val upstream = me.bind(in)

          override val upstreams = Set(upstream)

          override val toString: String = s"flatMap"

          override protected[this] def dryRun()(implicit runContext: RunContext) = {
            import DryRun._
            upstream.dryRunResult flatMap {
              case Unneeded(r) => fn(r).bind(UpstreamBoundMandate(r, this)).dryRunResult
              case Needed => Future.successful(Needed)
              case Blocked => Future.successful(Blocked)
            }
          }

          override protected[this] def run()(implicit runContext: RunContext) = {
            import Run._
            upstream.runResult flatMap {
              case Unneeded(mr) => fn(mr).bind(UpstreamBoundMandate(mr, this)).runResult
              case Done(mr) =>
                // We need to return done if this returned Done to indicate that an action was taken.
                fn(mr).bind(UpstreamBoundMandate(mr, this)).runResult map {
                  case Done(yr) => Done(yr)
                  case Unneeded(yr) => Done(yr)
                  case Blocked => Blocked
                }
              case Blocked => Future.successful(Blocked)
            } recover {
              case _ => Blocked
            }
          }
        }
    }

  def flatMap[C](you: Mandate[B, C]): Mandate[A, C] =
    this.flatMap( _ => you )

//  def flatMap[C >: A, B](you: OpenMandate[C, B])(implicit runContext: RunContext): MandateInput[B] = you.bind(me)

  /** Creates a new OpenMandate that, when bound, will produce two joined mandates (executed in parallel). */
  def join[C <: A, D](you: Mandate[C, D]): Mandate[C, (B, D)] =
    new Mandate[C, (B, D)] {
      override def bind(in: UpstreamBoundMandate[C])(implicit runContext: RunContext): UpstreamBoundMandate[(B, D)] =
        new BoundMandate[(B, D)] {
          import runContext.executionContext

          private[this] val mbm = me.bind(in)
          private[this] val ybm = you.bind(in)

          override val upstreams = Set(mbm, ybm)

          override val toString: String = s"join"

          override def dryRun()(implicit runContext: RunContext): Future[DryRun.Result[(B, D)]] = {
            import DryRun._

            // These need to be done outside of the callback structure below to ensure they happen in parallel.
            val mf = mbm.dryRunResult
            val yf = ybm.dryRunResult

            mf flatMap { mr =>
              yf map { yr =>
                (mr, yr) match {
                  case (Unneeded(mo), Unneeded(yo)) => Unneeded(mo, yo)
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
            val mf = me.bind(in).runResult
            val yf = you.bind(in).runResult

            mf flatMap { mr =>
              yf map { yr =>
                (mr, yr) match {
                  case (Unneeded(mo), Unneeded(yo)) => Unneeded(mo, yo)
                  case (lc: Completed[B], rc: Completed[D]) => Done(lc.output, rc.output)
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

  def ignore: Mandate[A, Nothing] = this map { _ => null.asInstanceOf[Nothing] }

  def compositeAs(description: String)(implicit runContext: RunContext) = new CompositeMandate(description, me)
}

object Mandate {
  /** To start a chain of Mandate combinators, when necessary. */
  def apply[A]: Mandate[A, A] = new Mandate[A, A] {
    override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext): UpstreamBoundMandate[A] = in
  }
}
