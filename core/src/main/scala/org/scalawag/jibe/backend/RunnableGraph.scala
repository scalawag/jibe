package org.scalawag.jibe.backend

import java.io.PrintWriter
import java.util.concurrent.Semaphore

import scala.language.higherKinds
import scala.collection.mutable.Builder
import scala.concurrent.Promise
import scala.util.Try
import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}
import org.scalawag.jibe.Logging.log
import org.scalawag.jibe.backend.RunnableGraph._

object RunnableGraph {
  trait Payload[-R] {
    def run(runContext: R): Boolean
    def abort(runContext: R): Unit
  }

  sealed trait Element[-R, +P <: Payload[R]]
  sealed trait Vertex[-R, +P <: Payload[R]] extends Element[R, P]
  case class PayloadVertex[-R, +P <: Payload[R]](payload: P) extends Vertex[R, P]
  case class SemaphoreVertex[-R, +P <: Payload[R]](permits: Int, name: Option[String] = None) extends Vertex[R, P]

  object Vertex {
    def apply[R, P <: Payload[R]](payload: P) = new PayloadVertex[R, P](payload)
  }

  sealed trait Edge[-R, +P <: Payload[R]] extends Element[R, P] {
    val from: Vertex[R, P]
    val to: Vertex[R, P]
  }
  case class PayloadToPayloadEdge[-R, +P <: Payload[R]](from: PayloadVertex[R, P], to: PayloadVertex[R, P]) extends Edge[R, P]
  case class PayloadToSemaphoreEdge[-R, +P <: Payload[R]](from: PayloadVertex[R, P], to: SemaphoreVertex[R, P]) extends Edge[R, P]
  case class SemaphoreToPayloadEdge[-R, +P <: Payload[R]](from: SemaphoreVertex[R, P], to: PayloadVertex[R, P]) extends Edge[R, P]

  object Edge {
    def apply[R, P <: Payload[R]](from: PayloadVertex[R, P], to: PayloadVertex[R, P]) = PayloadToPayloadEdge(from, to)
    def apply[R, P <: Payload[R]](from: PayloadVertex[R, P], to: SemaphoreVertex[R, P]) = new PayloadToSemaphoreEdge(from, to)
    def apply[R, P <: Payload[R]](from: SemaphoreVertex[R, P], to: PayloadVertex[R, P]) = new SemaphoreToPayloadEdge(from, to)
  }

  def afterAll[A, M[X] <: TraversableOnce[X]](futures: M[Future[A]])
                                             (implicit cbf: CanBuildFrom[M[Future[A]], Try[A], M[Try[A]]], ec: ExecutionContext): Future[M[Try[A]]] = {
    futures.foldLeft(Future.successful(cbf())) { (triesFuture, nextFuture) =>
      val p = Promise[Builder[Try[A], M[Try[A]]]]
      triesFuture onSuccess { case tries =>
        nextFuture onComplete { t =>
          p.success(tries += t)
        }
      }
      p.future
    }.map(_.result())
  }
}

/*
RunnableGraph is a directed graph.  Each vertex is either a SemaphoreVertex or a PayloadVertex.  Running the graph
means to call one of the Payload methods (run or abort) on each Payload in the graph exactly once.  Payloads will
only be run after the payloads of all other PayloadVertices with in-edges ("upstream" payloads) are run. If a Payload
fails (run() returns false or throws an exception), then all downstream payloads (including transitively) are aborted.
PayloadVertices with no in-edges will be run immediately (barring threading constraints) when the run is started.

SemaphoreVertices are handled outside of the algorithm described above. A semaphore-to-payload edge means that the
payload can not be run until a permit is issued from that semaphore. A payload-to-semaphore edge means that a permit
is released to the semaphore when that payload run() is complete (success or fail).  There are no such thing as
semaphore-to-semaphore edges.

Does not store individual instances of PayloadVertices.  If you add two vertices that both have equivalent payloads,
they will be treated as the same vertex.

Concurrency is controlled with the ExecutionContext passed into the RunnableGraph.run() method (maybe implicitly).
*/

class RunnableGraph[R, P <: Payload[R]](val vertices: Set[Vertex[R, P]] = Set.empty[Vertex[R, P]],
                                        val edges: Set[Edge[R, P]] = Set.empty[Edge[R, P]]) {

  def +(vertex: Vertex[R, P]) = new RunnableGraph[R, P](vertices + vertex, edges)

  def +(edge: Edge[R, P]) = {
    new RunnableGraph[R, P](vertices + edge.from + edge.to, edges + edge)
  }

  // These are some faster-lookup maps that we'll use when actually traversing the graph.

  private[this] lazy val payloadVertices = vertices.collect {
    case p: PayloadVertex[R, P] => p
  }

  private[this] lazy val payloadEdgesOut = edges.collect {
    case p: PayloadToPayloadEdge[R, P] => p
  }.groupBy(_.from).mapValues(_.map(_.to))

  private[this] lazy val payloadEdgesIn = edges.collect {
    case p: PayloadToPayloadEdge[R, P] => p
  }.groupBy(_.to).mapValues(_.map(_.from))

  private[this] lazy val semaphoreEdgesOut = edges.collect {
    case p: PayloadToSemaphoreEdge[R, P] => p
  }.groupBy(_.from).mapValues(_.map(_.to))

  private[this] lazy val semaphoreEdgesIn = edges.collect {
    case p: SemaphoreToPayloadEdge[R, P] => p
  }.groupBy(_.to).mapValues(_.map(_.from))

  private[this] def defaultVertexToAttributes(v: Vertex[R, P]): Map[String, Any] = v match {

    case sv: SemaphoreVertex[R, P] =>
      val name = sv.name.getOrElse("<unnamed>")
      val label = s"$name (${sv.permits})"
      Map("label" -> label, "shape" -> "box", "color" -> "red")

    case pv: PayloadVertex[R, P] =>
      val label = pv.payload.toString
      Map("label" -> label, "shape" -> "box")

  }

  def toDot(pw: PrintWriter, vertexToAttributes: (Vertex[R, P] => Map[String, Any]) = defaultVertexToAttributes _) = {
    pw.println("digraph RunnableGraph {")
    pw.println("rankdir=LR")

    def id(v: Vertex[R, P]) = System.identityHashCode(v)

    vertices foreach { v =>
      val attrString = vertexToAttributes(v).map { case (k, v) =>
        s"""${k}="${v}""""
      }.mkString("[",",","]")

      pw.println(s""""${id(v)}"$attrString""")
    }

    for {
      (from, tos) <- payloadEdgesOut
      to <- tos
    } {
      pw.println(s""""${id(from)}" -> "${id(to)}"""")
    }

    for {
      (from, tos) <- semaphoreEdgesOut
      to <- tos
    } {
      if ( semaphoreEdgesIn(from).contains(to) )
        pw.println(s""""${id(from)}" -> "${id(to)}"[dir=both]""")
      else
        pw.println(s""""${id(from)}" -> "${id(to)}"""")
    }

    for {
      (to, froms) <- semaphoreEdgesIn
      from <- froms
    } {
      // Only write this edge if there's not a corresponding out edge.  In that case, we write a "both" edge above.
      if ( ! semaphoreEdgesIn(to).contains(from) )
        pw.println(s""""${id(from)}" -> "${id(to)}"""")
    }

    pw.println("}")
  }

  def findCycle(root: PayloadVertex[R, P]): Option[List[PayloadVertex[R, P]]] = {
    def helper(path: List[PayloadVertex[R, P]]): Option[List[PayloadVertex[R, P]]] =
      path match {
        case Nil => None
        case head :: tail =>
          if ( tail.contains(head) )
            Some(head :: tail.takeWhile(_ != head).reverse)
          else
            payloadEdgesOut.getOrElse(head, Set.empty).flatMap( d => helper(d :: path) ).headOption
      }

    helper(List(root))
  }

  def run(runContext: R)(implicit ec: ExecutionContext) = {
    val t = new Traversal(runContext)
    t.start()
  }

  private[this] class Traversal(runContext: R)(implicit ec: ExecutionContext) {

    // Initialized to contain all of the edges in the graph.  As we traverse them, we'll remove them.
    // This is a separate class to make it easier to protect the actual maps with synchronization.

    private[this] object unusedEdgeMap {
      private[this] var unusedPayloadEdgesOut = payloadEdgesOut
      private[this] var unusedPayloadEdgesIn = payloadEdgesIn

      def downstreamPayloadVertices(from: PayloadVertex[R, P]): Set[PayloadVertex[R, P]] = synchronized {
        unusedPayloadEdgesOut.getOrElse(from, Set.empty)
      }

      // Returns the set of edges remaining that point into "to" while still holding the lock.  This is to ensure
      // that exactly one remover sees the empty set come back and will know that they can run that node.
      def removeEdge(from: PayloadVertex[R, P], to: PayloadVertex[R, P]) = synchronized {
        val remainingOuts = unusedPayloadEdgesOut(from) - to
        if ( remainingOuts.isEmpty )
          unusedPayloadEdgesOut -= from
        else
          unusedPayloadEdgesOut += ( from -> remainingOuts )

        val remainingIns = unusedPayloadEdgesIn(to) - from
        if ( remainingIns.isEmpty )
          unusedPayloadEdgesIn -= to
        else
          unusedPayloadEdgesIn += ( to -> remainingIns )

        remainingIns
      }

      def verifyEmptiness(): Unit = {
        if ( unusedPayloadEdgesIn.nonEmpty || unusedPayloadEdgesOut.nonEmpty ) {
          val inPayloadEdgesString =
            for {
              (to, froms) <- unusedPayloadEdgesIn
              from <- froms
            } yield {
              s"$to <- $from"
            } mkString("\n  in-edges:\n  ","\n  ","")

          val outPayloadEdgesString =
            for {
              (from, tos) <- unusedPayloadEdgesOut
              to <- tos
            } yield {
              s"$from -> $to"
            } mkString("\n  out-edges:\n  ","\n  ","")

          throw new IllegalStateException(s"Not all edges were traversed during the run.  This is a bug.  These edges remain:$inPayloadEdgesString$outPayloadEdgesString")
        }
      }
    }

    // We need to create Semaphores to represent the SemaphoreVertices for this run.  These are stand-ins that must
    // be created anew for each run or else multiple runs will interact with each other through the shared Semaphores.
    // This object synchronizes access the semaphore edges (not the semaphores themselves) so that we can ensure that
    // we've consumed all the edge by the end of the run.

    private[this] object unusedSemaphores {
      private[this] val semaphoreMap = vertices.collect {
        case sv: SemaphoreVertex[R, P] => ( sv -> new Semaphore(sv.permits) )
      }.toMap

      private[this] var unusedSemaphoreEdgesOut = semaphoreEdgesOut
      private[this] var unusedSemaphoreEdgesIn = semaphoreEdgesIn

      // These get and remove in one shot because there's no interesting logic between what would be the two calls
      // in the payload-to-payload world.

      def acquireUpstreamSemaphores(from: PayloadVertex[R, P]): Unit = {
        unusedSemaphoreEdgesIn.getOrElse(from, Set.empty).foreach { sv =>
          val s = semaphoreMap(sv)
          log.debug(s"acquiring semaphore ${sv.name.getOrElse(System.identityHashCode(sv))} for vertex ${from.payload}")
          s.acquire()
        }
        synchronized(unusedSemaphoreEdgesIn -= from)
      }

      def releaseDownstreamSemaphores(from: PayloadVertex[R, P]): Unit = {
        unusedSemaphoreEdgesOut.getOrElse(from, Set.empty).foreach { sv =>
          val s = semaphoreMap(sv)
          log.debug(s"releasing semaphore ${sv.name.getOrElse(System.identityHashCode(sv))} for vertex ${from.payload}")
          s.release()
        }
        synchronized(unusedSemaphoreEdgesOut -= from)
      }

      def verifyEmptiness(): Unit = {
        if ( unusedSemaphoreEdgesOut.nonEmpty || unusedSemaphoreEdgesIn.nonEmpty ) {
          val inSemaphoreEdgesString =
            for {
              (to, froms) <- unusedSemaphoreEdgesIn
              from <- froms
            } yield {
              s"$to <- $from"
            } mkString("\n  in-edges:\n  ","\n  ","")

          val outSemaphoreEdgesString =
            for {
              (from, tos) <- unusedSemaphoreEdgesOut
              to <- tos
            } yield {
              s"$from -> $to"
            } mkString("\n  out-edges:\n  ","\n  ","")

          throw new IllegalStateException(s"Not all edges were traversed during the run.  This is a bug.  These edges remain:$inSemaphoreEdgesString$outSemaphoreEdgesString")
        }
      }
    }

    private[this] object vertexStates {
      private[this] var states = Map.empty[PayloadVertex[R, P], Boolean]

      // These methods tell the caller whether they did the marking or if someone beat them to it.
      // Only one thread could be trying to mark a vertex as "run" because it has to be the thread that
      // removed the last in edge.  However, multiple threads can be marking a vertex as aborted.  More importantly,
      // different threads could be modifying different vertices' states at the same time.

      private[this] def mark(vertex: PayloadVertex[R, P], run: Boolean) = synchronized {
        states.get(vertex) match {
          case Some(b) =>
            // It's already been marked. That's an error. The algorithm should have prevented that.
            throw new IllegalStateException(s"already marked vertex being marked again: $b -> $run")
          case None =>
            // This thread gets to mark its vertex
            states += ( vertex -> run )
            true
        }
      }

      def markRun(vertex: PayloadVertex[R, P]): Boolean = mark(vertex, true)

      def markAborted(vertex: PayloadVertex[R, P]): Boolean = mark(vertex, false)

      def verifyCompleteness(): Unit = {
        val unvisitedVertices = payloadVertices -- states.keySet
        if ( unvisitedVertices.nonEmpty ) {
          val unvisitedVertivesString = unvisitedVertices.mkString("\n  ","\n  ","")
          throw new IllegalStateException(s"Not all vertices were visited during the run.  This is a bug.  These were skipped:$unvisitedVertivesString")
        }
      }
    }

    // Makes it easier to detect errors where Unit too easily becomes Future[Unit].
    private[this] case class UniqueReturn()

    def start() = {
      val roots = payloadVertices.filter( v => ! payloadEdgesIn.contains(v) )
      val f: Future[Set[Try[UniqueReturn]]] = afterAllFlat(roots.map(run))
      f map { _ =>
        unusedEdgeMap.verifyEmptiness()
        unusedSemaphores.verifyEmptiness()
        vertexStates.verifyCompleteness()
      }
    }

    /* Completes when all of the futures complete, one way or another. */

    private[this] def afterAllFlat(futures: Set[Future[Set[Try[UniqueReturn]]]]): Future[Set[Try[UniqueReturn]]] =
      afterAll(futures) map { fs =>
        fs.foldLeft(Set.empty[Try[UniqueReturn]]) { (acc, f) =>
          acc ++ f.get // TODO: what happens if one of these is a failure?
        }
    }

    private[this] def run(vertex: PayloadVertex[R, P]): Future[Set[Try[UniqueReturn]]] = {
      unusedSemaphores.acquireUpstreamSemaphores(vertex)
      log.debug(s"running vertex: $vertex")
      vertexStates.markRun(vertex)
      Future {
        vertex.payload.run(runContext)
      } flatMap { proceed =>
        unusedSemaphores.releaseDownstreamSemaphores(vertex)
        if ( proceed ) {
          log.debug("run() returned true, running downstream vertices")
          runDownstream(vertex)
        } else {
          log.debug("run() returned false, aborting downstream vertices")
          abortDownstream(vertex)
        }
      } recoverWith { case ex =>
        unusedSemaphores.releaseDownstreamSemaphores(vertex)
        log.debug(s"run() threw $ex, aborting downstream vertices")
        abortDownstream(vertex)
      }
    }

    private[this] def runDownstream(from: PayloadVertex[R, P]): Future[Set[Try[UniqueReturn]]] = afterAllFlat {
      unusedEdgeMap.downstreamPayloadVertices(from) map { to =>
        if (unusedEdgeMap.removeEdge(from, to).isEmpty) {
          run(to)
        } else {
          Future.successful(Set(Try(UniqueReturn())))
       }
      }
    }

    private[this] def abortDownstream(from: PayloadVertex[R, P], chain: Seq[PayloadVertex[R, P]] = Seq.empty): Future[Set[Try[UniqueReturn]]] = afterAllFlat {
      unusedEdgeMap.downstreamPayloadVertices(from) map { to =>
        unusedEdgeMap.removeEdge(from, to)
        if ( vertexStates.markAborted(to) ) {
          log.debug(s"aborting vertex: $to")
          Future {
            to.payload.abort(runContext)
          } flatMap { proceed =>
            abortDownstream(to, chain)
          } recoverWith { case _ =>
            abortDownstream(to, chain)
          }
        } else {
          Future.successful(Set(Try(UniqueReturn())))
        }
      }
    }
  }
}
