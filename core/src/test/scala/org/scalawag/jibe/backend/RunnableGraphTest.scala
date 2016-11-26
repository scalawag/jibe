package org.scalawag.jibe.backend

import java.io.{PrintStream, PrintWriter}
import java.util.concurrent.{LinkedBlockingQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.{Logging, TestLogging}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class RunnableGraphTest extends FunSpec with Matchers {
  TestLogging

  sealed trait TestSignal
  case object Stop extends TestSignal
  case object Go extends TestSignal

  sealed trait TestState
  case object Succeeded extends TestState
  case object Failed extends TestState
  case object Stopped extends TestState

  private[this] val ex = new RuntimeException("boom")

  private[this] case class VertexCall(signals: List[Option[TestSignal]], result: Try[Option[TestState]], enter: Long, exit: Long)

  private[this] class TestVisitContext {
    var calls = Map.empty[TestRunnableGraphFactory.TestVertex, List[VertexCall]]

    def dump(ps: PrintStream): Unit = {
      val pw = new PrintWriter(ps)
      dump(pw)
      pw.flush()
    }

    def dump(pw: PrintWriter): Unit = {
      calls foreach { case (vertex, vcalls) =>
        pw.println(vertex)
        vcalls foreach { case vcall =>
          pw.println("  " + vcall)
        }
      }
    }
  }

  private[this] object TestRunnableGraphFactory extends RunnableGraphFactory {
    override type VisitContextType = TestVisitContext

    class TestVertex(name: String,
                     result: Try[TestState] = Success(Succeeded),
                     duration: FiniteDuration = 100.milliseconds,
                     val semaphores: Set[Semaphore] = Set.empty) extends Vertex {

      override type SignalType = TestSignal
      override type StateType = TestState
      override val semaphoresToAcquire = semaphores
      override val semaphoresToRelease = semaphores

      private[this] def recordCall(signals: List[Option[TestSignal]])(fn: => Option[TestState])(implicit visitContext: TestVisitContext) = {
        val enter = System.currentTimeMillis()
        val result = Try(fn)
        visitContext.synchronized {
          visitContext.calls += this -> ( VertexCall(signals, result, enter, System.currentTimeMillis()) :: visitContext.calls.getOrElse(this, Nil) )
        }
        result.get
      }

      override def visit(signals: List[Option[TestSignal]])(implicit visitContext: TestVisitContext) = recordCall(signals) {
        if ( signals.exists(_ == Some(Stop)) ) {
          Thread.sleep(duration.toMillis)
          Some(Stopped)
        } else if ( signals.exists(_ == None) ) {
          None
        } else {
          Thread.sleep(duration.toMillis)
          Some(result.get)
        }
      }

      override def toString = s"V($name)"
    }

    implicit def signalVertex(state: Try[TestState]): TestSignal = state match {
      case Success(Succeeded) => Go
      case _ => Stop
    }
  }
  import TestRunnableGraphFactory._

//  private[this] implicit val payloaderSignalFn = { (outcome: Boolean) =>
//    if ( outcome )
//      YouMayProceed
//    else
//      YouveBeenBlocked
//  }

  private[this] implicit val executionContext = {
    val threadGroup = new ThreadGroup("RunnableGraph")

    val threadFactory = new ThreadFactory {
      private[this] var serial = -1

      override def newThread(r: Runnable) = {
        serial += 1
        new Thread(threadGroup, r, "RunnableGraph-%03d".format(serial))
      }
    }

    val e = new ThreadPoolExecutor(10, 100, 100L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable], threadFactory)
    e.allowCoreThreadTimeOut(true)
    ExecutionContext.fromExecutor(e)
  }
  

  private[this] def assertAllNodesVisited(graph: RunnableGraph)(implicit visitContext: TestVisitContext) = {
    graph.vertices.foreach {
      case tv: TestVertex => tv.call(visitContext)
//      case pv: Payloader => pv.call(visitContext)
//      case sv: SemaphoreVertex => // NOOP
//      case sv: Countermander => // NOOP
    }
  }

  private[this] implicit class VertexPimper(v1: TestVertex) {
    def call(implicit visitContext: TestVisitContext) =
      visitContext.calls(v1).head

    def enter(implicit visitContext: TestVisitContext) =
      call.enter

    def exit(implicit visitContext: TestVisitContext) =
      call.exit

    def ranConcurrentlyWith(v2: TestVertex)(implicit rc: TestVisitContext) =
      if ( v1.enter <= v2.exit )
        v1.exit >= v2.enter
      else if ( v2.enter <= v1.exit )
        v2.exit >= v1.enter
      else
        false

    def didNotRunConcurrentlyWith(v2: TestVertex)(implicit rc: TestVisitContext) =
      // v1 occurred entirely before v2 or v1's exit happened in the same millisecond and v2's enter
      if ( v1.enter < v2.exit )
        v1.exit <= v2.enter
      // v2 occurred entirely before v1 or v2's exit happened in the same millisecond and v1's enter
      else if ( v2.enter < v1.exit )
        v2.exit <= v1.enter
      // v2 occurred entirely before v1 or v2's exit happened in the same millisecond and v1's enter
      else
        math.signum(v1.exit - v1.enter) != math.signum(v2.exit - v2.enter)

    def shouldNotHaveRunConcurrentlyWith(v2: TestVertex)(implicit rc: TestVisitContext) =
      if ( ! ( v1 didNotRunConcurrentlyWith v2 ) )
        fail(s"$v1 should not have run concurrently with $v2 (${v1.enter}-${v1.exit} ${v2.enter}-${v2.exit})")

    def shouldHaveRunConcurrentlyWith(v2: TestVertex)(implicit rc: TestVisitContext) =
      if ( ! ( v1 ranConcurrentlyWith v2 ) )
        fail(s"$v1 should have run concurrently with $v2 (${v1.enter}-${v1.exit} ${v2.enter}-${v2.exit})")

    def shouldPrecede(v2: TestVertex)(implicit rc: TestVisitContext) = {
      v1.exit should be <= v2.enter
    }
  }

  it ("should run a vertex") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += va

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    Logging.log.debug { pw: PrintWriter =>
      visitContext.dump(pw)
    }

    assertAllNodesVisited(g)
    va.call.result shouldBe Success(Some(Succeeded))
  }

  it ("should run independent vertices concurrently") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b")

    g += va
    g += vb

    implicit val visitContext = new TestVisitContext

    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)
    va shouldHaveRunConcurrentlyWith vb

    va.call.result shouldBe Success(Some(Succeeded))
    vb.call.result shouldBe Success(Some(Succeeded))
  }

  it ("should be able to run the same graph multiple times") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += va

    val visitContext1 = new TestVisitContext
    Await.result(g.run(visitContext1), Duration.Inf)
    assertAllNodesVisited(g)(visitContext1)
    va.call(visitContext1).result shouldBe Success(Some(Succeeded))

    val visitContext2 = new TestVisitContext
    Await.result(g.run(visitContext2), Duration.Inf)
    assertAllNodesVisited(g)(visitContext2)
    va.call(visitContext2).result shouldBe Success(Some(Succeeded))
  }

  it ("should be able to run the same graph multiple times concurrently") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += va

    val visitContext1 = new TestVisitContext
    val future1 = g.run(visitContext1)

    val visitContext2 = new TestVisitContext
    val future2 = g.run(visitContext2)

    Await.result(Future.sequence(Iterable(future1, future2)), Duration.Inf)

    assertAllNodesVisited(g)(visitContext1)
    va.call(visitContext1).result shouldBe Success(Some(Succeeded))

    assertAllNodesVisited(g)(visitContext2)
    va.call(visitContext2).result shouldBe Success(Some(Succeeded))

    va.call(visitContext1).enter should be < va.call(visitContext2).exit
    va.call(visitContext2).enter should be < va.call(visitContext1).exit
  }

  it ("should run dependent vertices in strict order") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b")

    g += Edge(va, vb)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    va.call.result shouldBe Success(Some(Succeeded))
    vb.call.result shouldBe Success(Some(Succeeded))
  }

  it ("should abort downstream vertices on throw") {
    var g = new RunnableGraph

    val va = new TestVertex("a", result = Failure(ex))
    val vb = new TestVertex("b")

    g += Edge(va, vb)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    va.call.result shouldBe Failure(ex)
    vb.call.result shouldBe Success(Some(Stopped))
  }

  it ("should abort downstream vertices transitively on throw") {
    var g = new RunnableGraph

    val va = new TestVertex("a", result = Failure(ex))
    val vb = new TestVertex("b")
    val vc = new TestVertex("c")

    g += Edge(va, vb)
    g += Edge(vb, vc)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    vb shouldPrecede vc

    va.call.result shouldBe Failure(ex)
    vb.call.result shouldBe Success(Some(Stopped))
    vc.call.result shouldBe Success(Some(Stopped))
  }

  it ("should abort downstream vertices on false") {
    var g = new RunnableGraph

    val va = new TestVertex("a", result = Success(Failed))
    val vb = new TestVertex("b")

    g += Edge(va, vb)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    va.call.result shouldBe Success(Some(Failed))
    vb.call.result shouldBe Success(Some(Stopped))
  }

  it ("should abort downstream vertices transitively on false") {
    var g = new RunnableGraph

    val va = new TestVertex("a", result = Success(Failed))
    val vb = new TestVertex("b")
    val vc = new TestVertex("c")

    g += Edge(va, vb)
    g += Edge(vb, vc)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    vb shouldPrecede vc

    va.call.result shouldBe Success(Some(Failed))
    vb.call.result shouldBe Success(Some(Stopped))
    vc.call.result shouldBe Success(Some(Stopped))
  }

  it ("should handle vertices with multiple in-edges") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b")
    val vc = new TestVertex("c")
    val vd = new TestVertex("d")

    g += Edge(va, vb)
    g += Edge(vb, vd)
    g += Edge(va, vc)
    g += Edge(vc, vd)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    // We should know that d has to be aborted prior to c executing successfully.

    va shouldPrecede vb
    va shouldPrecede vc
    vb shouldPrecede vd
    vc shouldPrecede vd
    vb shouldHaveRunConcurrentlyWith vc

    va.call.result shouldBe Success(Some(Succeeded))
    vb.call.result shouldBe Success(Some(Succeeded))
    vc.call.result shouldBe Success(Some(Succeeded))
    vd.call.result shouldBe Success(Some(Succeeded))
  }

  it ("should abort vertices with multiple unresolved in-edges immediately") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b", result = Failure(ex))
    val vc = new TestVertex("c")
    val vd = new TestVertex("d")

    g += Edge(va, vb)
    g += Edge(vb, vd)
    g += Edge(va, vc)
    g += Edge(vc, vd)

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    // We should know that d has to be aborted aborted prior to c executing successfully.

    va shouldPrecede vb
    va shouldPrecede vc
    vd.exit should be > vc.exit

    va.call.result shouldBe Success(Some(Succeeded))
    vb.call.result shouldBe Failure(ex)
    vc.call.result shouldBe Success(Some(Succeeded))
    vd.call.result shouldBe Success(Some(Stopped))
  }

  it ("should use semaphores properly") {
    var g = new RunnableGraph

    val s = new Semaphore(1)
    val va = new TestVertex("a")
    val vb = new TestVertex("b", semaphores = Set(s))
    val vc = new TestVertex("c", semaphores = Set(s))
    val vd = new TestVertex("d", semaphores = Set(s))

    g += Edge(va, vb)
    g += Edge(va, vc)
    g += Edge(va, vd)

//    g += Edge(vb, vs)
//    g += Edge(vs, vb)
//
//    g += Edge(vc, vs)
//    g += Edge(vs, vc)
//
//    g += Edge(vd, vs)
//    g += Edge(vs, vd)

    val vl = new VisitListener {
      override def onVisit[V <: TestRunnableGraphFactory.Vertex](v: V, signals: Iterable[Option[V#SignalType]]) = {
        println(s"visit: $v $signals")
      }
      override def onState[V <: TestRunnableGraphFactory.Vertex](v: V, state: V#StateType)  = {
        println(s"state: $v $state")
      }
      override def onSignal[E <: TestRunnableGraphFactory.Edge](e: E, signal: E#ToType#SignalType)  = {
        println(s"visit: $e $signal")
      }
    }

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext, Some(vl)), Duration.Inf)

    assertAllNodesVisited(g)

    va.call.result shouldBe Success(Some(Succeeded))
    vb.call.result shouldBe Success(Some(Succeeded))
    vc.call.result shouldBe Success(Some(Succeeded))
    vd.call.result shouldBe Success(Some(Succeeded))

    va shouldPrecede vb
    va shouldPrecede vc
    va shouldPrecede vd


    // The three nodes that are tied to the semaphore should not run concurrently

    vb shouldNotHaveRunConcurrentlyWith vc
    vc shouldNotHaveRunConcurrentlyWith vd
    vb shouldNotHaveRunConcurrentlyWith vd
  }

  it ("should detect no cycle") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b")
    val vc = new TestVertex("c")

    g += Edge(va, vb)
    g += Edge(vb, vc)

    g.findCycle(va) shouldBe None
  }

  it ("should detect single-vertex cycle") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += Edge(va, va)

    g.findCycle(va) shouldBe Some(List(va))
  }

  it ("should detect multiple-vertex cycle") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b")
    val vc = new TestVertex("c")

    g += Edge(va, vb)
    g += Edge(vb, vc)
    g += Edge(vc, va)

    g.findCycle(va) shouldBe Some(List(va, vb, vc))
  }
/*
  it ("should countermand a vertex") {
    var g = new RunnableGraph

    val va = new TestVertex("a", returns = false)
    val vb = new TestVertex("b")
    val vc = new TestVertex("c")

    val c = new Countermander()

    g += Edge(va, vb)
    g += Edge(vb, vc)

    g += Edge(va, c){ o: Boolean => if ( o ) Uphold else Countermand }
    g += Edge(c, vb)

Logging.log.debug("MARK")

    implicit val visitContext = new TestVisitContext
    Await.result(g.run(visitContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    vb shouldPrecede vc

    va.call.signals shouldBe YouMayProceed
    vb.call.signals shouldBe YouveBeenCountermanded
    vc.call.signals shouldBe YouMayProceed
  }
*/
}
