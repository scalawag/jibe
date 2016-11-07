package org.scalawag.jibe.backend

import java.io.PrintWriter

import scala.language.higherKinds
import scala.collection.mutable.Builder
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}
import org.scalawag.jibe.Logging.log
import org.scalawag.jibe.multitree.OnlyIdentityEquals

class RunnableGraphFactory {
  type VisitContextType
  type VisitOutcomeType
  type PayloadType <: Payload

  trait Payload {
    def onProceed(runContext: VisitContextType): VisitOutcomeType
    def onBlocked(runContext: VisitContextType): Unit
    def onCountermanded(runContext: VisitContextType): Unit
  }

  sealed trait Vertex {
    type SignalType
    type RunContextType
  }

  case class Semaphore(permits: Int)

  case class Payloader(payload: Payload,
                       semaphoresToAcquire: Set[Semaphore] = Set.empty,
                       semaphoresToRelease: Set[Semaphore] = Set.empty)
    extends Vertex
  {
    override type SignalType = PayloaderSignal
    type PayloadType <: Payload
  }

  class Countermander(name: Option[String] = None) extends Vertex {
    override type SignalType = CountermanderSignal
  }

  sealed trait PayloaderSignal
  case object YouMayProceed extends PayloaderSignal
  case object YouveBeenBlocked extends PayloaderSignal
  case object YouveBeenCountermanded extends PayloaderSignal

  sealed trait CountermanderSignal
  case object Countermand extends CountermanderSignal
  case object Uphold extends CountermanderSignal

  sealed trait Edge {
    type FromType <: Vertex
    type ToType <: Vertex

    val from: FromType
    val to: ToType
  }

  case class PayloaderToPayloaderEdge(from: Payloader, to: Payloader)(implicit val signalFn: VisitOutcomeType => PayloaderSignal) extends Edge {
    override type FromType = Payloader
    override type ToType = Payloader
  }

  case class PayloaderToCountermanderEdge(from: Payloader, to: Countermander)(implicit val signalFn: VisitOutcomeType => CountermanderSignal) extends Edge {
    override type FromType = Payloader
    override type ToType = Countermander
  }

  case class CountermanderToPayloaderEdge(from: Countermander, to: Payloader) extends Edge {
    override type FromType = Countermander
    override type ToType = Payloader
  }

  object Edge {
    def apply(from: Payloader, to: Payloader)(implicit signalFn: VisitOutcomeType => PayloaderSignal) =
      new PayloaderToPayloaderEdge(from, to)(signalFn)

    def apply(from: Payloader, to: Countermander)(implicit signalFn: VisitOutcomeType => CountermanderSignal) =
      new PayloaderToCountermanderEdge(from, to)(signalFn)

    def apply(from: Countermander, to: Payloader) =
      new CountermanderToPayloaderEdge(from, to)
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

  case class RunnableGraph(val vertices: Set[Vertex] = Set.empty, val edges: Set[Edge] = Set.empty) {

    def +(vertex: Vertex) = RunnableGraph(vertices + vertex, edges)

    def +(edge: Edge) = RunnableGraph(vertices + edge.from + edge.to, edges + edge)

    // These are some faster-lookup maps that we'll use when actually traversing the graph.

    private[this] lazy val payloadVertices = vertices.collect {
      case p: Payloader => p
    }

    private[this] lazy val payloadEdgesOut = edges.collect {
      case p: PayloaderToPayloaderEdge => p
    }.groupBy(_.from).mapValues(_.map(_.to))

    private[this] lazy val payloadEdgesIn = edges.collect {
      case p: PayloaderToPayloaderEdge => p
    }.groupBy(_.to).mapValues(_.map(_.from))

    //  private[this] lazy val semaphoreEdgesOut = edges.collect {
    //    case p: PayloadToSemaphoreEdge[R, P] => p
    //  }.groupBy(_.from).mapValues(_.map(_.to))
    //
    //  private[this] lazy val semaphoreEdgesIn = edges.collect {
    //    case p: SemaphoreToPayloadEdge[R, P] => p
    //  }.groupBy(_.to).mapValues(_.map(_.from))

    private[this] def defaultVertexToAttributes(v: Vertex): Map[String, Any] = v match {

      //    case sv: SemaphoreVertex[R, P] =>
      //      val name = sv.name.getOrElse("<unnamed>")
      //      val label = s"$name (${sv.permits})"
      //      Map("label" -> label, "shape" -> "box", "color" -> "red")

      case pv: Payloader =>
        val label = pv.payload.toString
        Map("label" -> label, "shape" -> "box")

    }

    def toDot(pw: PrintWriter, vertexToAttributes: (Vertex => Map[String, Any]) = defaultVertexToAttributes _) = {
      pw.println("digraph RunnableGraph {")
      pw.println("rankdir=LR")

      def id(v: Vertex) = System.identityHashCode(v)

      vertices foreach { v =>
        val attrString = vertexToAttributes(v).map { case (k, v) =>
          s"""${k}="${v}""""
        }.mkString("[", ",", "]")

        pw.println(s""""${id(v)}"$attrString""")
      }

      for {
        (from, tos) <- payloadEdgesOut
        to <- tos
      } {
        pw.println(s""""${id(from)}" -> "${id(to)}"""")
      }
      /*
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
  */
      pw.println("}")
    }

    def findCycle(root: Payloader): Option[List[Payloader]] = {
      def helper(path: List[Payloader]): Option[List[Payloader]] =
        path match {
          case Nil => None
          case head :: tail =>
            if (tail.contains(head))
              Some(head :: tail.takeWhile(_ != head).reverse)
            else
              payloadEdgesOut.getOrElse(head, Set.empty).flatMap(d => helper(d :: path)).headOption
        }

      helper(List(root))
    }

    def run(runContext: VisitContextType)(implicit ec: ExecutionContext): Future[Unit] = {
      val t = new Traversal(runContext)
      t.start()
    }

    private[this] class Traversal(visitContext: VisitContextType)(implicit ec: ExecutionContext) {

      // Makes it easier to detect errors where Unit too easily becomes Future[Unit].
      private[this] case class UniqueReturn()

      // Create a shadow graph of mutable vertices and edges that we can use to keep track of the state of the traversal.
      // These are the elements we'll use.

      private[this] abstract class ShadowVertex[+V <: Vertex](val original: V) {
        type State
        type Signal

        // Vertices with in-edges to this vertex are keys in the map.  The values is the signals received from that
        // vertex along that edge.  A value of None means that we have not heard from that vertex yet.

        var ins = Set.empty[ShadowEdge[_]]

        // Vertices with out-edges to this vertex.  We don't keep track of the signals that we sent them.  They do
        // (see above).  The Booleans in this map indicate whether or not we have sent a signal, but that's all.

        var outs = Set.empty[ShadowEdge[_]]

        // What's the state of this vertex, if it's been determined.  None means that the jury's still out.

        var state: Option[State] = None

        // Check to see if there's anything that we can do based on the signals that we've collected.

        def visit(): Future[Set[Try[UniqueReturn]]]

        override def toString = original.toString
      }

      sealed trait PayloaderState
      case object Countermanded extends PayloaderState
      case object Blocked extends PayloaderState
      case object Succeeded extends PayloaderState
      case object Failed extends PayloaderState

      sealed trait CountermanderState
      case object Complete extends CountermanderState


      private[this] class ShadowPayloader(override val original: Payloader) extends ShadowVertex[Payloader](original) {
        override type State = PayloaderState
        override type Signal = PayloaderSignal

  //      unusedEdgeMap.downstreamPayloadVertices(from) map { to =>
  //        if (unusedEdgeMap.removeEdge(from, to).isEmpty) {
  //          run(to)
  //        } else {
  //          Future.successful(Set(Try(UniqueReturn())))
  //        }
  //      }
  //
/*
        private[this] def setState(newState: State)(fn: => Future[Set[Try[UniqueReturn]]]): Future[Set[Try[UniqueReturn]]] = {
          if ( this.state == None ) {
            // It doesn't have a state yet, change the state and notify the outs.
            fn
          } else if ( this.state == newState ) {
            // We're already in the right state, there's nothing else to do.
            Future.successful(Set(Try(UniqueReturn())))
          } else {
            // Oops.  It already had a different state.  This shouldn't happen.
            throw new IllegalStateException(s"trying to change vertex $this state to $newState when it is already in state $state")
          }
        }
*/
        override def visit(): Future[Set[Try[UniqueReturn]]] = {
          val payloaderSignal: Option[PayloaderSignal] =
            if ( ins.map(_.signal).exists( _ contains YouveBeenBlocked ) )
              Some(YouveBeenBlocked)
            else if ( ins.map(_.signal).exists( _ contains YouveBeenCountermanded ) )
              Some(YouveBeenCountermanded)
            else if ( ins.map(_.signal).forall( _ contains YouMayProceed ) )
              Some(YouMayProceed)
            else
              None

          payloaderSignal match {
            case None =>
              Future.successful(Set(Try(UniqueReturn()))) // Nothing to do here yet.

            case Some(signal) =>
  //            if ( signal == Proceed )
  //              unusedSemaphores.acquireUpstreamSemaphores(vertex)

              log.debug(s"visiting vertex $this with signal $signal")

              val visitFuture: Future[VisitOutcomeType] = Future {
                signal match {
                  case YouMayProceed =>
                    original.payload.onProceed(visitContext)
                  case YouveBeenBlocked =>
                    original.payload.onBlocked(visitContext)
                    null.asInstanceOf[VisitOutcomeType] // ignored below
                  case YouveBeenCountermanded =>
                    original.payload.onCountermanded(visitContext)
                    null.asInstanceOf[VisitOutcomeType] // ignored below
                }
              }

              mapAll(visitFuture) { results =>

                if ( signal == YouMayProceed )
                  null // unusedSemaphores.releaseDownstreamSemaphores(null)

                val futures =
                  results match {
                    case Success(outcome) =>
                      log.debug(s"visit() returned $outcome, signaling downstream vertices")
                      outs map {
                        case ppe: ShadowPayloaderToPayloaderEdge =>
                          val downstreamSignal = signal match {
                            case YouMayProceed => ppe.original.signalFn(outcome)
                            case _ => signal
                          }
                          ppe.send(downstreamSignal)

                        case pce: ShadowPayloaderToCountermanderEdge =>
                          val downstreamSignal = signal match {
                            case YouMayProceed => pce.original.signalFn(outcome)
                            case _ => Uphold
                          }
                          pce.send(downstreamSignal)

                      }

                    case Failure(ex) =>
                      log.debug(s"visit() threw $ex, signaling downstream vertices to Uphold and Block")
                      outs map {
                        case ppe: ShadowPayloaderToPayloaderEdge => ppe.send(YouveBeenBlocked)
                        case pce: ShadowPayloaderToCountermanderEdge => pce.send(Uphold)
                      }
                  }



                afterAllFlat(futures) // Send signals to all downstream vertices and wait for them to complete.
              }
          }
        }
      }

      private[this] class ShadowCountermander(original: Countermander) extends ShadowVertex[Countermander](original) {
        override type State = CountermanderState
        override type Signal = CountermanderSignal

        override def visit(): Future[Set[Try[UniqueReturn]]] = {
          val payloaderSignal: Option[PayloaderSignal] =
            // If we've received any Countermand signals, we can go ahead and tell the downstream Payloaders that
            // they've been countermanded.
            if ( ins.map(_.signal).exists( _ contains Countermand ) )
              Some(YouveBeenCountermanded)

            // If we've received all of our signals and none of them are Countermands, we can tell the downstream
            // Payloaders that they can proceed.  Nothing's going to change from here on out.
            else if ( ins.map(_.signal).forall( _ contains Uphold ) )
              Some(YouMayProceed)

            // If neither of the above cases hold, we still may receive an Countermand, so we can't tell the
            // downstream Payloaders anything yet.
            else
              None

          payloaderSignal match {
            case None =>
              Future.successful(Set(Try(UniqueReturn()))) // Nothing to do here yet, stop walking.

            case Some(signal) =>
              val futures =
                outs map {
                  case cpe: ShadowCountermanderToPayloaderEdge => cpe.send(signal)
                }

              afterAllFlat(futures) // Send signals to all downstream vertices and wait for them to complete.

          }
        }
      }

//      private[this] class ShadowSemaphoreVertex(original: SemaphoreVertex[R, P]) extends ShadowVertex

  //    private[this] sealed trait ShadowEdge[F <: ShadowVertex, T <: ShadowVertex] {
  //      val from: F
  //      val to: T
  //    }

      private[this] abstract class ShadowEdge[E <: Edge](original: E, from: ShadowVertex[E#FromType], to: ShadowVertex[E#ToType]) {
        // This is set once the edge has been used to signal.  Prior to that, it's set to None.

        var signal: Option[E#ToType#SignalType] = None

        // Called by the from vertex whenever it's ready to send the to vertex a signal along this edge.  This
        // should happen exactly once for each edge during the traversal.

        def send(signal: E#ToType#SignalType): Future[Set[Try[UniqueReturn]]] = {
          this.signal = Some(signal)
          this.to.visit()
        }

        override def toString = original.toString
      }

      private[this] case class ShadowPayloaderToPayloaderEdge(original: PayloaderToPayloaderEdge, from: ShadowPayloader, to: ShadowPayloader)
        extends ShadowEdge[PayloaderToPayloaderEdge](original, from, to)
      private[this] case class ShadowPayloaderToCountermanderEdge(original: PayloaderToCountermanderEdge, from: ShadowPayloader, to: ShadowCountermander)
        extends ShadowEdge[PayloaderToCountermanderEdge](original, from, to)
      private[this] case class ShadowCountermanderToPayloaderEdge(original: CountermanderToPayloaderEdge, from: ShadowCountermander, to: ShadowPayloader)
        extends ShadowEdge[CountermanderToPayloaderEdge](original, from, to)

      // And this is the field that contains the shadow graph.

      private[this] val shadowVertices = {
        // First, build a map of all the new shadow vertices, keyed off the original vertex.

        val vertexMap = vertices map { originalVertex =>

          val shadowVertex = originalVertex match {
            case pv: Payloader => new ShadowPayloader(pv)
            case dv: Countermander => new ShadowCountermander(dv)
//            case sv: SemaphoreVertex => new ShadowSemaphoreVertex(sv)
          }

          originalVertex -> shadowVertex
        } toMap

        // Now, go through all of the original edges and add them to the shadow vertices.

        edges foreach { edge =>
          val shadowFrom = vertexMap(edge.from)
          val shadowTo = vertexMap(edge.to)
          val shadowEdge = edge match {
            case ppe: PayloaderToPayloaderEdge =>
              new ShadowPayloaderToPayloaderEdge(ppe, shadowFrom.asInstanceOf[ShadowPayloader], shadowTo.asInstanceOf[ShadowPayloader])
            case pce: PayloaderToCountermanderEdge =>
              new ShadowPayloaderToCountermanderEdge(pce, shadowFrom.asInstanceOf[ShadowPayloader], shadowTo.asInstanceOf[ShadowCountermander])
            case cpe: CountermanderToPayloaderEdge =>
              new ShadowCountermanderToPayloaderEdge(cpe, shadowFrom.asInstanceOf[ShadowCountermander], shadowTo.asInstanceOf[ShadowPayloader])
          }

          shadowFrom.outs += shadowEdge
          shadowTo.ins += shadowEdge
        }

        vertexMap.values.toSet
      }

      // Initialized to contain all of the edges in the graph.  As we traverse them, we'll remove them.
      // This is a separate class to make it easier to protect the actual maps with synchronization.
/*
      private[this] object unusedEdgeMap {
        private[this] var unusedPayloadEdgesOut = payloadEdgesOut
        private[this] var unusedPayloadEdgesIn = payloadEdgesIn

        def downstreamPayloadVertices(from: Payloader): Set[Payloader] = synchronized {
          unusedPayloadEdgesOut.getOrElse(from, Set.empty)
        }

        // Returns the set of edges remaining that point into "to" while still holding the lock.  This is to ensure
        // that exactly one remover sees the empty set come back and will know that they can run that node.
        def removeEdge(from: Payloader, to: Payloader) = synchronized {
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

        def acquireUpstreamSemaphores(from: Payloader[R, P]): Unit = {
          unusedSemaphoreEdgesIn.getOrElse(from, Set.empty).foreach { sv =>
            val s = semaphoreMap(sv)
            log.debug(s"acquiring semaphore ${sv.name.getOrElse(System.identityHashCode(sv))} for vertex ${from.payload}")
            s.acquire()
          }
          synchronized(unusedSemaphoreEdgesIn -= from)
        }

        def releaseDownstreamSemaphores(from: Payloader[R, P]): Unit = {
          unusedSemaphoreEdgesOut.getOrElse(from, Set.empty).foreach { sv =>
            val s = semaphoreMap(sv)
            log.debug(s"releasing semaphore ${sv.name.getOrElse(System.identityHashCode(sv))} for vertex ${from.payload}")
            s.release()
          }
          synchronized(unusedSemaphoreEdgesOut -= from)
        }
      }

      private[this] object vertexStates {
        private[this] var states = Map.empty[Payloader[R, P], Boolean]

        // These methods tell the caller whether they did the marking or if someone beat them to it.
        // Only one thread could be trying to mark a vertex as "run" because it has to be the thread that
        // removed the last in edge.  However, multiple threads can be marking a vertex as aborted.  More importantly,
        // different threads could be modifying different vertices' states at the same time.

        private[this] def mark(vertex: Payloader[R, P], run: Boolean) = synchronized {
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

        def markRun(vertex: Payloader[R, P]): Boolean = mark(vertex, true)

        def markAborted(vertex: Payloader[R, P]): Boolean = mark(vertex, false)

        def verifyCompleteness(): Unit = {
          val unvisitedVertices = payloadVertices -- states.keySet
          if ( unvisitedVertices.nonEmpty ) {
            val unvisitedVertivesString = unvisitedVertices.mkString("\n  ","\n  ","")
            throw new IllegalStateException(s"Not all vertices were visited during the run.  This is a bug.  These were skipped:$unvisitedVertivesString")
          }
        }
      }
*/
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
/*
      private[this] def run(vertex: Payloader[R, P]): Future[Set[Try[UniqueReturn]]] = {
        unusedSemaphores.acquireUpstreamSemaphores(vertex)
        log.debug(s"running vertex: $vertex")
        vertexStates.markRun(vertex)
        Future {
          vertex.payload.run(visitContext)
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

      private[this] def runDownstream(from: Payloader[R, P]): Future[Set[Try[UniqueReturn]]] = afterAllFlat {
        unusedEdgeMap.downstreamPayloadVertices(from) map { to =>
          if (unusedEdgeMap.removeEdge(from, to).isEmpty) {
            run(to)
          } else {
            Future.successful(Set(Try(UniqueReturn())))
         }
        }
      }

      private[this] def abortDownstream(from: Payloader[R, P], chain: Seq[Payloader[R, P]] = Seq.empty): Future[Set[Try[UniqueReturn]]] = afterAllFlat {
        unusedEdgeMap.downstreamPayloadVertices(from) map { to =>
          unusedEdgeMap.removeEdge(from, to)
          if ( vertexStates.markAborted(to) ) {
            log.debug(s"aborting vertex: $to")
            Future {
              to.payload.abort(visitContext)
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
*/
    }
  }

//  case class SemaphoreVertex[R, P <: Payload[R]](permits: Int, name: Option[String] = None) extends Vertex[R, P] with OnlyIdentityEquals
//


//  object Edge {
//    def apply[R, P <: Payload[R]](from: PayloadVertex[R, P], to: PayloadVertex[R, P]) = PayloadToPayloadEdge(from, to)
//    def apply[R, P <: Payload[R]](from: Payloader[R, P], to: SemaphoreVertex[R, P]) = new PayloadToSemaphoreEdge(from, to)
//    def apply[R, P <: Payload[R]](from: SemaphoreVertex[R, P], to: Payloader[R, P]) = new SemaphoreToPayloadEdge(from, to)
//    def apply[R, P <: Payload[R]](from: PayloadVertex[R, P], to: DefeatVertex[R, P]) = new PayloadToDefeatEdge(from, to)
//    def apply[R, P <: Payload[R]](from: DefeatVertex[R, P], to: PayloadVertex[R, P]) = new DefeatToPayloadEdge(from, to)
//  }

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

  def mapAll[A, B](future: Future[A])(fn: Try[A] => Future[B])(implicit ec: ExecutionContext): Future[B] =
    future flatMap { a =>
      fn(Success(a))
    } recoverWith {
      case ex => fn(Failure(ex))
    }
}

