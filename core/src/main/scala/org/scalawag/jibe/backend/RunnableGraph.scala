package org.scalawag.jibe.backend

import scala.language.higherKinds

import java.io.PrintWriter
import scala.collection.mutable.Builder
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}
import org.scalawag.jibe.Logging.log
import java.util.concurrent.{Semaphore => JSemaphore}

import org.scalawag.jibe.ReadThroughCache

class RunnableGraphFactory {
  type VisitContextType
  type VertexType <: Vertex

  class Semaphore(val permits: Int = 1, val name: Option[String] = None)

  trait VisitListener {
    def onVisit[V <: Vertex](v: V, signals: Iterable[Option[V#SignalType]]): Unit
    def onState[V <: Vertex](v: V, state: V#StateType): Unit
    def onSignal[E <: Edge](e: E, signal: E#ToType#SignalType): Unit
  }

  trait Vertex {
    // These are collected on in-edges to determine what the vertex does.
    type SignalType
    // This indicates the final state of this Vertex
    type StateType

    // When this method returns Some, it means that it did what it's going to do and its state is current.  When it
    // returns None, it means that it still doesn't have enough signals to do its thing.  After it returns Some, the
    // visit() method will not be called again.  Prior to that, every time it is called, it should have more signals
    // available, although this is not enforced.
    def visit(signals: List[Option[SignalType]])(implicit visitContext: VisitContextType): Option[StateType]

    val semaphoresToAcquire: Set[Semaphore] = Set.empty
    val semaphoresToRelease: Set[Semaphore] = Set.empty
  }

  trait Edge {
    type FromType <: Vertex
    type ToType <: Vertex

    val from: FromType
    val to: ToType

    def signal(fromState: Try[FromType#StateType]): ToType#SignalType
  }

  object Edge {
    def apply[F <: Vertex, T <: Vertex](f: F, t: T)(implicit signalFn: Try[F#StateType] => T#SignalType) = {
      new Edge {
        final override type FromType = F
        final override type ToType = T
        final override val from = f
        final override val to = t
        final override def signal(fromState: Try[F#StateType]) = signalFn(fromState)
        final override def toString = s"Edge($from -> $to)"
      }
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

  case class RunnableGraph(vertices: Set[Vertex] = Set.empty, edges: Set[Edge] = Set.empty) {

    def +(vertex: Vertex) = RunnableGraph(vertices + vertex, edges)

    def +(edge: Edge) = RunnableGraph(vertices + edge.from + edge.to, edges + edge)

    // The strange looking type specifications here server to make these invariant members know that they can contain
    // anything that derives from Vertex instead of trying to use the (narrower) types defined by the Edge trait.
    private[this] lazy val edgesOut = edges.groupBy(_.from: Vertex).mapValues(_.map(_.to: Vertex))
    private[this] lazy val edgesIn = edges.groupBy(_.to: Vertex).mapValues(_.map(_.from: Vertex))

    private[this] def defaultVertexToAttributes(v: Vertex): Map[String, Any] = {
      Map("label" -> v.toString, "shape" -> "box")
    }

    def toDot(pw: PrintWriter, vertexToAttributes: (Vertex => Map[String, Any]) = defaultVertexToAttributes _) = {
      pw.println("digraph RunnableGraph {")
      pw.println("rankdir=LR")

      def id(v: Vertex) = System.identityHashCode(v)

      vertices foreach { v =>
        val attrString = vertexToAttributes(v).map { case (k, v) =>
          s"""$k="$v""""
        }.mkString("[", ",", "]")

        pw.println(s""""${id(v)}"$attrString""")
      }

      for {
        (from, tos) <- edgesOut
        to <- tos
      } {
        pw.println(s""""${id(from)}" -> "${id(to)}"""")
      }

      pw.println("}")
    }

    def findCycle(root: Vertex): Option[List[Vertex]] = {
      def helper(path: List[Vertex]): Option[List[Vertex]] =
        path match {
          case Nil => None
          case head :: tail =>
            if (tail.contains(head))
              Some(head :: tail.takeWhile(_ != head).reverse)
            else
              edgesOut.getOrElse(head, Set.empty).flatMap(d => helper(d :: path)).headOption
        }

      helper(List(root))
    }

    def run(visitContext: VisitContextType, listener: Option[VisitListener] = None)(implicit ec: ExecutionContext): Future[Unit] = {
      val t = new Traversal(visitContext, listener)
      t.start()
    }

    private[this] class Traversal(visitContext: VisitContextType, listener: Option[VisitListener] = None)(implicit ec: ExecutionContext) {

      // Makes it easier to detect errors where Unit too easily becomes Future[Unit].
      private[this] case class UniqueReturn()

      // Create the Java Semaphores to represent the Semaphores for this run.  These are stand-ins that must be
      // created anew for each run or else multiple runs will interact with each other through the shared Java
      // Semaphores.

      private[this] val semaphoreMap = new ReadThroughCache[Semaphore, JSemaphore](s => new JSemaphore(s.permits))

      // Create a shadow graph of mutable vertices and edges that we can use to keep track of the state of the traversal.
      // These are the elements we'll use.

      private[this] class ShadowVertex[+V <: Vertex](val original: V) {

        // Vertices with in-edges to this vertex are keys in the map.  The values is the signals received from that
        // vertex along that edge.  A value of None means that we have not heard from that vertex yet.

        var ins: Set[ShadowEdge] = Set.empty

        // Vertices with out-edges to this vertex.  We don't keep track of the signals that we sent them.  They do
        // (see above).  The Booleans in this map indicate whether or not we have sent a signal, but that's all.

        var outs: Set[ShadowEdge] = Set.empty

        // What's the state of this vertex, if it's been determined.  None means that the jury's still out.

        var state: Option[original.StateType] = None

        // Visit the vertex.  It should check to see if there's anything that it can determine about its state from
        // the signals that we've collected.

        def visit(): Future[Set[Try[UniqueReturn]]] = {
          val signals = ins.toList.map(_.signal.map(_.asInstanceOf[original.SignalType]))

          log.debug(s"visiting vertex $this with signals $signals")

          val visitFuture = Future {
            acquireSemaphores()
            try {
              original.visit(signals)(visitContext)
            } finally {
              releaseSemaphores()
              listener.foreach(_.onVisit(original, signals))
            }
          }

          visitFuture flatMap {
            case Some(state) =>
              listener.foreach(_.onState(original, state))
              log.debug(s"$this entered state $state, signaling downstream vertices")
              val futures =
                outs map { e =>
                  e.send(Success(state.asInstanceOf[e.original.FromType#StateType]))
                }
              afterAllFlat(futures)

            case None =>
              log.debug(s"$this has not entered a state, signaling no one")
              Future.successful(Set(Success(UniqueReturn()): Try[UniqueReturn]))

          } recoverWith {
            case ex =>
              log.debug(s"this threw $ex, signaling downstream vertices")
              val futures =
                outs map { e =>
                  e.send(Failure(ex))
                }
              afterAllFlat(futures)
          }
        }

        private[this] def acquireSemaphores(): Unit =
          original.semaphoresToAcquire.foreach { s =>
            val js = semaphoreMap.getOrCreate(s)
            log.debug(s"acquiring semaphore ${s.name.getOrElse(System.identityHashCode(s))} for vertex $this")
            js.acquire()
          }

        private[this] def releaseSemaphores(): Unit =
          original.semaphoresToRelease.foreach { s =>
            val js = semaphoreMap.getOrCreate(s)
            log.debug(s"releasing semaphore ${s.name.getOrElse(System.identityHashCode(s))} for vertex $this")
            js.release()
          }

        override def toString = original.toString
      }

      private[this] abstract class ShadowEdge(val original: Edge) {
        val from: ShadowVertex[original.FromType]
        val to: ShadowVertex[original.ToType]

        // This is set once the edge has been used to signal.  Prior to that, it's set to None.

        var signal: Option[original.ToType#SignalType] = None

        // Called by the from vertex whenever it's ready to send the to vertex a signal along this edge.  This
        // should happen exactly once for each edge during the traversal.

        def send(state: Try[original.FromType#StateType]): Future[Set[Try[UniqueReturn]]] = {
          val signal = original.signal(state)
          this.signal = Some(signal)
          listener.foreach(_.onSignal(original, signal))
          log.debug(s"updating edge signal to ${this.signal} based on state $state")
          this.to.visit()
        }

        override def toString = original.toString
      }

      // And this is the field that contains the shadow graph.

      private[this] val shadowVertices = {
        // First, build a map of all the new shadow vertices, keyed off the original vertex.

        val vertexMap = vertices map { originalVertex =>
          val shadowVertex = new ShadowVertex(originalVertex)
          originalVertex -> shadowVertex
        } toMap

        // Now, go through all of the original edges and add them to the shadow vertices.
        // All these casts are safe because we just added the vertices to the map, so we know what they are even
        // though they're not statically typed as such because of the heterogeneous nature of the map.

        edges foreach { edge =>
          val shadowFrom = vertexMap(edge.from).asInstanceOf[ShadowVertex[edge.FromType]]
          val shadowTo = vertexMap(edge.to).asInstanceOf[ShadowVertex[edge.ToType]]
          val shadowEdge = new ShadowEdge(edge) {
            override val from = shadowTo.asInstanceOf[ShadowVertex[original.FromType]]
            override val to = shadowTo.asInstanceOf[ShadowVertex[original.ToType]]
          }

          shadowFrom.outs += shadowEdge
          shadowTo.ins += shadowEdge
        }

        vertexMap.values.toSet
      }

      def start() = {
        val roots = shadowVertices.filter(_.ins.isEmpty)
        val f: Future[Set[Try[UniqueReturn]]] = afterAllFlat(roots.map(_.visit()))
        f map { _ =>
//          unusedEdgeMap.verifyEmptiness()
//          vertexStates.verifyCompleteness()
        }
      }

      /* Completes when all of the futures complete, one way or another. */

      private[this] def afterAllFlat(futures: Set[Future[Set[Try[UniqueReturn]]]]): Future[Set[Try[UniqueReturn]]] =
        afterAll(futures) map { fs =>
          fs.foldLeft(Set.empty[Try[UniqueReturn]]) { (acc, f) =>
            acc ++ f.get // TODO: what happens if one of these is a failure?
          }
      }
    }
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

  def mapTry[A, B](future: Future[A])(fn: Try[A] => Future[B])(implicit ec: ExecutionContext): Future[B] =
    future flatMap { a =>
      fn(Success(a))
    } recoverWith {
      case ex => fn(Failure(ex))
    }
}

