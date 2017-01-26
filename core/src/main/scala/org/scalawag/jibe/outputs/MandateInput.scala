package org.scalawag.jibe.outputs

import java.io.PrintWriter

import scala.concurrent.Future

/** An abstraction for the inputs required by a mandate which may come from static code or may come from the output
  * of another mandate.
  *
  * @tparam A the output type
  */

trait MandateInput[+A] { me =>
  def dryRunResult: Future[DryRun.Result[A]]
  def runResult: Future[Run.Result[A]]

  val inputs: Set[MandateInput[_]] = Set.empty // TODO: statically type

  /** Produces a new MandateInput which returns the same dry-run and run results as this mandate with the output
    * (if present) transformed using the provided function. For results that have no output, the function is not used.
    */

  def map[B](fn: A => B)(implicit runContext: RunContext): Mandate[B] =
    new Mandate[B] {
      import runContext.executionContext

      override val inputs: Set[MandateInput[_]] = Set(me)
      override val toString: String = s"map fn"

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

  // TODO: flatMap may be the wrong name for this since it doesn't pass the output of the first to the second.
/*
  NB: This breaks the pattern by not actually creating a dependencies from the output of one to the other and therefore
  NB: shouldn't exist.  The correct way to do what this is doing is to map the output of this mandate to the input
  NB: of the second unbound (even if this means wholesale replacement - i.e. { _ => X }) mandate and then flatmap
  NB: to the unbound mandate.

  def flatMap[B](you: MandateInput[B])(implicit runContext: RunContext): Mandate[B] =
    new Mandate[B] {
      import runContext.executionContext

      override val inputs: Set[MandateInput[_]] = Set(me, you)
      override val toString: String = s"flatMap"

      override protected[this] def dryRun()(implicit runContext: RunContext) = {
        // TODO: Validate everything I say here with respect to desired behavior.
        // Since the output of the first (me) is not needed for the input of the second (you), we can go ahead and
        // run these in any order.  We want to perform a dry-run on each even if the first one fails or is blocked,
        // since there's no actual dependency.
        me.dryRunResult
        you.dryRunResult
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
        } recover {
          case _ => Blocked
        }
      }
    }
*/

  /*
    Follow up to the above: what if you need to refer to the input of an outer mandate (like the argument) multiple
    times or even replace the output with that value (or a mapping of it).  In that case, you want a function like
    this.  We just need to make sure that it's not misused.  See InstallSbt.

    The difference between this and the above is that this one takes a function that produces a MandateInput so that
    it's clearer that it can use its input to determine what to return.
  */

  def flatMap[B](fn: A => MandateInput[B])(implicit runContext: RunContext): Mandate[B] =
    new Mandate[B] {
      import runContext.executionContext

      override val inputs: Set[MandateInput[_]] = Set(me)
      override val toString: String = s"flatMap"

      override protected[this] def dryRun()(implicit runContext: RunContext) = {
        import DryRun._
        me.dryRunResult flatMap {
          case Unneeded(r) => fn(r).dryRunResult
          case Needed => Future.successful(Needed)
          case Blocked => Future.successful(Blocked)
        }
      }

      override protected[this] def run()(implicit runContext: RunContext) = {
        import Run._
        me.runResult flatMap {
          case Unneeded(mr) => fn(mr).runResult
          case Done(mr) =>
            // We need to return done if this returned Done to indicate that an action was taken.
            fn(mr).runResult map {
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

  /** Produces a new MandateInput which calculates the results of this input and then, if it is complete, binds the
    * output to the provided open mandate, which provides the output of the returned input.  If this input returns an
    * incomplete result (Blocked or Needed), then that result is used as the result of the new input and the
    * provided mandate's result is not calculated.
    */

  def flatMap[C >: A, B](you: OpenMandate[C, B])(implicit runContext: RunContext): MandateInput[B] = you.bind(me)

  /** Produces a new MandateInput which calculates the results of this input and the provided input in parallel and
    * then returns the results of both combined in a tuple.  If either input returns an incomplete result (Blocked
    * or Needed), then the result of the new input is the same.  If either input returns a Done result, the new input
    * returns a Done.  Otherwise, when both inputs return Unneeded results, the result of the new input is Unneeded.
    */

  def join[C >: A, B](you: MandateInput[B])(implicit runContext: RunContext): MandateInput[(C,B)] =
    new Mandate[(C, B)] {
      import runContext.executionContext

      override val inputs: Set[MandateInput[_]] = Set(me, you)
      override val toString: String = s"join"

      override def dryRun()(implicit runContext: RunContext): Future[DryRun.Result[(A, B)]] = {
        import DryRun._

        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val mf = me.dryRunResult
        val yf = you.dryRunResult

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
        val mf = me.runResult
        val yf = you.runResult

        mf flatMap { mr =>
          yf map { yr =>
            (mr, yr) match {
              case (Unneeded(mo), Unneeded(yo)) => Unneeded(mo, yo)
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

  def compositeAs(description: String)(implicit runContext: RunContext) = new CompositeMandate[A](description, me)

  def graph(pw: PrintWriter): Unit = {
    pw.println("digraph RunnableGraph {")
    pw.println("  rankdir=LR")

    var vertices: Set[Int] = Set.empty
    var edges: Set[(Int, Int)] = Set.empty

    def id(mi: MandateInput[_]) = System.identityHashCode(mi)

    def addEdge(from: Int, to: Int): Unit =
      if ( ! edges.contains((from, to)) ) {
        pw.println(s""""$from" -> "$to"""")
        edges += ((from, to))
      }

    def addToGraph(mi: MandateInput[_]): Unit = mi match {
      case cm: CompositeMandate[_] =>
        if ( ! vertices.contains(id(mi)) ) {
          val attrs = Map("shape" -> "cds", "label" -> cm.toString)
          val attrString = attrs.map { case (k, v) =>
            s"""${k}="${v}""""
          }.mkString("[",",","]")

          pw.println(s""""${id(mi)}"$attrString""")

          mi.inputs foreach addToGraph

          mi.inputs foreach { from =>
            addEdge(id(from), id(mi))
          }
        }

      case m: Mandate[_] =>
        if ( ! vertices.contains(id(mi)) ) {
          val attrs = Map("shape" -> "box", "label" -> m.toString)
          val attrString = attrs.map { case (k, v) =>
            s"""${k}="${v}""""
          }.mkString("[",",","]")

          pw.println(s""""${id(mi)}"$attrString""")

          mi.inputs foreach addToGraph

          mi.inputs foreach {
            case from: CompositeMandate[_] =>
              addEdge(id(from), id(mi))
            case from: Mandate[_] =>
              addEdge(id(from), id(mi))
            case _ =>
          }
        }

      case _ => None
    }

    addToGraph(this)
    pw.println("}")
  }

  def dump(pw: PrintWriter, depth: Int = 0): Unit = this match {
    case m: Mandate[_] =>
      pw.println(" " * depth + m)
      m.inputs foreach { i =>
        i.dump(pw, depth + 2)
      }
    case m: CompositeMandate[_] =>
      println(" " * depth + m)
      m.inputs foreach { i =>
        i.dump(pw, depth + 2)
      }
    case m =>
      m.inputs foreach { i =>
        i.dump(pw, depth)
      }
  }

}

object MandateInput {
  implicit def fromLiteral[A](a: A): MandateInput[A] = new MandateInput[A] {
    override val toString: String = a.toString
    override val inputs: Set[MandateInput[_]] = Set.empty
    override val dryRunResult = Future.successful(DryRun.Unneeded(a))
    override val runResult = Future.successful(Run.Unneeded(a))
  }
}

