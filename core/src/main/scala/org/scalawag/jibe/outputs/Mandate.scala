package org.scalawag.jibe.outputs

import java.io.PrintWriter

import org.scalawag.jibe.outputs

import scala.concurrent.Future

trait Mandate[-A, +B] {
  /** Binds this mandate to a given input, producing a mandate which is ready to be executed. */
  def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext): UpstreamBoundMandate[B]

  def map[C](fn: B => C): Mandate[A, C] = new MapMandate(this, fn)
  def flatMap[C](fn: B => Mandate[B, C]): Mandate[A, C] = new FnFlatMapMandate(this, fn)
  def flatMap[C](that: Mandate[B, C]): Mandate[A, C] = new FlatMapMandate(this, that)
  def join[C <: A, D](that: Mandate[C, D]): Mandate[C, (B, D)] = new JoinMandate(this, that)

  def replace[C <: A, D](you: Mandate[C, D]): Mandate[C, D] = ( this join you ) map (_._2)

  def compositeAs(description: String) = new CompositeMandate(description, this)
}

object Mandate {
  /** To start a chain of Mandate combinators, when necessary. */
  def apply[A]: Mandate[A, A] = new Mandate[A, A] {
    override val toString: String = s"constant Mandate"
    override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext): UpstreamBoundMandate[A] = in
  }
}

class MapMandate[-A, +B, +C](upstream: Mandate[A, B], fn: B => C) extends Mandate[A, C] {
  override val toString: String = s"map"

  override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) = new Bound(in)

  class Bound(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) extends BoundMandate[C] {
    import runContext.executionContext

    override def toString = "map"

    private[this] val boundMandate = upstream.bind(in)

    override val upstreams = Iterable(boundMandate)

    override protected[this] def dryRun()(implicit runContext: RunContext) =
      boundMandate.dryRunResult map { drr =>
        drr.map(fn)
      }

    override protected[this] def run()(implicit runContext: RunContext) =
      boundMandate.runResult map { rr =>
        rr.map(fn)
      }
  }
}

class FlatMapMandate[-A, +B, +C](lUpstream: Mandate[A, B], rUpstream: Mandate[B, C]) extends Mandate[A, C] {
  override val toString: String = s"flatMap BAD"

  override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) = new Bound(in)

  class Bound(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) extends BoundMandate[C] {
    import runContext.executionContext

    private[this] val lBoundMandate = lUpstream.bind(in)

    override def toString = rUpstream.toString

    override val upstreams = Iterable(lBoundMandate)

    override protected[this] def dryRun()(implicit runContext: RunContext) = {
      import DryRun._
      lUpstream.bind(in).dryRunResult flatMap {
        case Unneeded(lValue) => rUpstream.bind(lValue).dryRunResult
        case Needed => Future.successful(Needed)
        case Blocked => Future.successful(Blocked)
      }
    }

    override protected[this] def run()(implicit runContext: RunContext) = {
      import Run._
      lUpstream.bind(in).runResult flatMap {
        case Unneeded(lValue) => rUpstream.bind(lValue).runResult
        case Done(lValue) =>
          // We need to return done if this returned Done to indicate that an action was taken.
          rUpstream.bind(lValue).runResult map {
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

class FnFlatMapMandate[-A, +B, +C](upstream: Mandate[A, B], fn: B => Mandate[B, C]) extends Mandate[A, C] {
  override val toString: String = s"flatMapFn"

  override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) = new Bound(in)

  class Bound(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) extends BoundMandate[C] {
    import runContext.executionContext

    override def toString = "flatMap fn"

    private[this] val boundMandate = upstream.bind(in)

    override val upstreams = Iterable(boundMandate)

    override protected[this] def dryRun()(implicit runContext: RunContext) = {
      import DryRun._
      boundMandate.dryRunResult flatMap {
        case Unneeded(r) => fn(r).bind(r).dryRunResult
        case Needed => Future.successful(Needed)
        case Blocked => Future.successful(Blocked)
      }
    }

    override protected[this] def run()(implicit runContext: RunContext) = {
      import Run._
      boundMandate.runResult flatMap {
        case Unneeded(mr) => fn(mr).bind(mr).runResult
        case Done(mr) =>
          // We need to return done if this returned Done to indicate that an action was taken.
          fn(mr).bind(mr).runResult map {
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

class JoinMandate[-A, +B, +C](lUpstream: Mandate[A, B], rUpstream: Mandate[A, C]) extends Mandate[A, (B, C)] {
  override val toString: String = s"join"

  override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) = new Bound(in)

  class Bound(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) extends BoundMandate[(B, C)] {
    import runContext.executionContext

    override def toString = "join"

    private[this] val lBoundMandate = lUpstream.bind(in)
    private[this] val rBoundMandate = rUpstream.bind(in)

    override val upstreams = Iterable(lBoundMandate, rBoundMandate)

    override def dryRun()(implicit runContext: RunContext): Future[DryRun.Result[(B, C)]] = {
      import DryRun._

      // These need to be done outside of the callback structure below to ensure they happen in parallel.
      val lFuture = lBoundMandate.dryRunResult
      val rFuture = rBoundMandate.dryRunResult

      lFuture flatMap { lResult =>
        rFuture map { rResult =>
          (lResult, rResult) match {
            case (Unneeded(lValue), Unneeded(rValue)) => Unneeded(lValue, rValue)
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

    override def run()(implicit runContext: RunContext): Future[Run.Result[(B, C)]] = {
      import Run._
      // These need to be done outside of the callback structure below to ensure they happen in parallel.
      val lFuture = lBoundMandate.runResult
      val rFuture = rBoundMandate.runResult

      lFuture flatMap { lResult =>
        rFuture map { rResult =>
          (lResult, rResult) match {
            case (Unneeded(lValue), Unneeded(rValue)) => Unneeded(lValue, rValue)
            case (lc: Completed[B], rc: Completed[C]) => Done(lc.output, rc.output)
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
