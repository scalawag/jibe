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

  private[this] class TestRunContext {
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
    override type RunContextType = TestRunContext

    class TestVertex(name: String, result: Try[TestState] = Success(Succeeded), duration: FiniteDuration = 100.milliseconds) extends Vertex {
      override type SignalType = TestSignal
      override type StateType = TestState

      private[this] def recordCall(signals: List[Option[TestSignal]])(fn: => Option[TestState])(implicit runContext: TestRunContext) = {
        val enter = System.currentTimeMillis()
        val result = Try(fn)
        runContext.synchronized {
          runContext.calls += this -> ( VertexCall(signals, result, enter, System.currentTimeMillis()) :: runContext.calls.getOrElse(this, Nil) )
        }
        result.get
      }

      override def visit(signals: List[Option[TestSignal]])(implicit runContext: TestRunContext) = recordCall(signals) {
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
  

  private[this] def assertAllNodesVisited(graph: RunnableGraph)(implicit runContext: TestRunContext) = {
    graph.vertices.foreach {
      case tv: TestVertex => tv.call(runContext)
//      case pv: Payloader => pv.call(runContext)
//      case sv: SemaphoreVertex => // NOOP
//      case sv: Countermander => // NOOP
    }
  }

  private[this] implicit class VertexPimper(v1: TestVertex) {
    def call(implicit runContext: TestRunContext) =
      runContext.calls(v1).head

    def enter(implicit runContext: TestRunContext) =
      call.enter

    def exit(implicit runContext: TestRunContext) =
      call.exit

    def ranConcurrentlyWith(v2: TestVertex)(implicit rc: TestRunContext) =
      ( ! ( v1.enter <= v2.exit ) || ( v1.exit >= v2.enter ) ) && ( ! ( v2.enter <= v1.exit ) || ( v2.exit >= v1.enter ) )

    def shouldNotHaveRunConcurrentlyWith(v2: TestVertex)(implicit rc: TestRunContext) = {
      if ( v1 ranConcurrentlyWith v2 )
        fail(s"$v1 should not have run concurrently with $v2 (${v1.enter}-${v1.exit} ${v2.enter}-${v2.exit})")
    }

    def shouldHaveRunConcurrentlyWith(v2: TestVertex)(implicit rc: TestRunContext) = {
      if ( ! ( v1 ranConcurrentlyWith v2 ) )
        fail(s"$v1 should have run concurrently with $v2 (${v1.enter}-${v1.exit} ${v2.enter}-${v2.exit})")
    }

    def shouldPrecede(v2: TestVertex)(implicit rc: TestRunContext) = {
      v1.exit should be <= v2.enter
    }
  }

  it ("should run a vertex") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += va

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    Logging.log.debug { pw: PrintWriter =>
      runContext.dump(pw)
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

    implicit val runContext = new TestRunContext

    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)
    va shouldHaveRunConcurrentlyWith vb

    va.call.result shouldBe Success(Some(Succeeded))
    vb.call.result shouldBe Success(Some(Succeeded))
  }

  it ("should be able to run the same graph multiple times") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += va

    val runContext1 = new TestRunContext
    Await.result(g.run(runContext1), Duration.Inf)
    assertAllNodesVisited(g)(runContext1)
    va.call(runContext1).result shouldBe Success(Some(Succeeded))

    val runContext2 = new TestRunContext
    Await.result(g.run(runContext2), Duration.Inf)
    assertAllNodesVisited(g)(runContext2)
    va.call(runContext2).result shouldBe Success(Some(Succeeded))
  }

  it ("should be able to run the same graph multiple times concurrently") {
    var g = new RunnableGraph

    val va = new TestVertex("a")

    g += va

    val runContext1 = new TestRunContext
    val future1 = g.run(runContext1)

    val runContext2 = new TestRunContext
    val future2 = g.run(runContext2)

    Await.result(Future.sequence(Iterable(future1, future2)), Duration.Inf)

    assertAllNodesVisited(g)(runContext1)
    va.call(runContext1).result shouldBe Success(Some(Succeeded))

    assertAllNodesVisited(g)(runContext2)
    va.call(runContext2).result shouldBe Success(Some(Succeeded))

    va.call(runContext1).enter should be < va.call(runContext2).exit
    va.call(runContext2).enter should be < va.call(runContext1).exit
  }

  it ("should run dependent vertices in strict order") {
    var g = new RunnableGraph

    val va = new TestVertex("a")
    val vb = new TestVertex("b")

    g += Edge(va, vb)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

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
/*
  it ("should use semaphores properly") {
    var g = new RunnableGraph

    val va = new TestVertex(new TestPayload("a"))
    val vb = new TestVertex(new TestPayload("b"))
    val vc = new TestVertex(new TestPayload("c"))
    val vd = new TestVertex(new TestPayload("d"))
    val vs = SemaphoreVertex(1)

    g += Edge(va, vb)
    g += Edge(va, vc)
    g += Edge(va, vd)

    g += Edge(vb, vs)
    g += Edge(vs, vb)

    g += Edge(vc, vs)
    g += Edge(vs, vc)

    g += Edge(vd, vs)
    g += Edge(vs, vd)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va.call.signals shouldBe YouMayProceed
    vb.call.signals shouldBe YouMayProceed
    vc.call.signals shouldBe YouMayProceed
    vd.call.signals shouldBe YouMayProceed

    va shouldPrecede vb
    va shouldPrecede vc
    va shouldPrecede vd


    // The three nodes that are tied to the semaphore should not run concurrently

    vb shouldNotHaveRunConcurrentlyWith vc
    vc shouldNotHaveRunConcurrentlyWith vd
    vb shouldNotHaveRunConcurrentlyWith vd
  }
*/

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

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    vb shouldPrecede vc

    va.call.signals shouldBe YouMayProceed
    vb.call.signals shouldBe YouveBeenCountermanded
    vc.call.signals shouldBe YouMayProceed
  }
*/
}
