package org.scalawag.jibe.outputs

import java.io.PrintWriter

import scala.concurrent.Future

/** Represents a mandate that has been bound to its RunContext and upstream BoundMandateInput.  This means that it
  * encapsulates all input to the mandate and
  * @param runContext
  * @tparam A
  */

/** Represents a thing to which a Mandate can be bound. */

trait UpstreamBoundMandate[+A] { me =>
  val upstreams: Iterable[UpstreamBoundMandate[_]]

  def dryRunResult: Future[DryRun.Result[A]]
  def runResult: Future[Run.Result[A]]

  def graph(pw: PrintWriter): Unit = {
    pw.println("digraph Mandates {")
    pw.println("  rankdir=LR")

    var vertices: Set[Int] = Set.empty
    var edges: Set[(Int, Int)] = Set.empty

    def id(mi: UpstreamBoundMandate[_]) = System.identityHashCode(mi)

    def addEdge(from: Int, to: Int): Unit =
      if ( ! edges.contains((from, to)) ) {
        pw.println(s""""$from" -> "$to"""")
        edges += ((from, to))
      }

    def addToGraph(mi: UpstreamBoundMandate[_]): Unit = mi match {
      case cm: CompositeMandate[_, _] =>
        if ( ! vertices.contains(id(mi)) ) {
          val attrs = Map("shape" -> "cds", "label" -> cm.toString)
          val attrString = attrs.map { case (k, v) =>
            s"""${k}="${v}""""
          }.mkString("[",",","]")

          pw.println(s""""${id(mi)}"$attrString""")

          mi.upstreams foreach addToGraph

          mi.upstreams foreach { from =>
            addEdge(id(from), id(mi))
          }
        }

      case m =>
        if ( ! vertices.contains(id(mi)) ) {
          val attrs = Map("shape" -> "box", "label" -> m.toString)
          val attrString = attrs.map { case (k, v) =>
            s"""${k}="${v}""""
          }.mkString("[",",","]")

          pw.println(s""""${id(mi)}"$attrString""")

          mi.upstreams foreach addToGraph

          mi.upstreams foreach {
            case from: CompositeMandate[_, _] =>
              addEdge(id(from), id(mi))
            case from: BoundMandate[_] =>
              addEdge(id(from), id(mi))
            case _ =>
          }
        }

      //      case _ => None
    }

    addToGraph(this)
    pw.println("}")
  }

  def dump(pw: PrintWriter, depth: Int = 0): Unit = this match {
//    case m: CompositeBoundMandate[_, _] =>
//      println(" " * depth + m)
//      m.upstreamMandates foreach { i =>
//        i.dump(pw, depth + 2)
//      }
    case m: BoundMandate[_] =>
      pw.println(" " * depth + m)
      m.upstreams foreach { i =>
        i.dump(pw, depth + 2)
      }
    case m =>
      pw.println(" " * depth + m)
      m.upstreams foreach { i =>
        i.dump(pw, depth)
      }
  }

}

object UpstreamBoundMandate {
  // This one maintains the upstream graph.  It's useful internally to ensure the graph is built properly.
  def apply[A](a: A, upstream: UpstreamBoundMandate[_]): UpstreamBoundMandate[A] = new UpstreamBoundMandate[A] {
    override val toString: String = s"Literal($a, $upstream)"
    override val upstreams = Iterable(upstream)
    override val dryRunResult = Future.successful(DryRun.Unneeded(a))
    override val runResult = Future.successful(Run.Unneeded(a))
  }

  // This one does not maintain the upstream graph (because it doesn't have one or it's not possible).
  def fromLiteral[A](a:A): UpstreamBoundMandate[A] = new UpstreamBoundMandate[A] {
    override val toString: String = s"Literal($a)"
    override val upstreams = Iterable.empty
    override val dryRunResult = Future.successful(DryRun.Unneeded(a))
    override val runResult = Future.successful(Run.Unneeded(a))
  }
}

