package org.scalawag.jibe.backend

import java.util.concurrent.{LinkedBlockingQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}
import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.FileUtils
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import RunnableGraph._

class RunnableGraphTest extends FunSpec with Matchers {

  private[this] case class PayloadCall(run: Boolean, enter: Long, exit: Long)

  private[this] class TestRunContext {
    var calls = Map.empty[Payload[TestRunContext], PayloadCall]
  }

  private[this] case class TestPayload(name: String, duration: FiniteDuration = 100.milliseconds, throws: Boolean = false, returns: Boolean = true) extends Payload[TestRunContext] {
    private def recordCall[A](runContext: TestRunContext, run: Boolean)(fn: => A): A = {
      val enter = System.currentTimeMillis()
      try {
        fn
      } finally {
        runContext.synchronized {
          if (runContext.calls.contains(this))
            throw new IllegalStateException(s"payload function called more than once for vertex: $this")
          runContext.calls += this -> PayloadCall(run, enter, System.currentTimeMillis())
        }
      }
    }

    override def run(runContext: TestRunContext) = recordCall(runContext, true) {
      Thread.sleep(duration.toMillis)
      if (throws)
        throw new RuntimeException("boom")
      returns
    }

    override def abort(runContext: TestRunContext) = recordCall(runContext, false) {
      Thread.sleep(10)
    }

    override val toString = name
  }

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

  private[this] def assertAllNodesVisited(graph: RunnableGraph[TestRunContext, TestPayload])(implicit runContext: TestRunContext) = {
    graph.vertices.foreach {
      case pv: PayloadVertex[TestRunContext, TestPayload] => pv.call(runContext)
      case sv: SemaphoreVertex[TestRunContext, TestPayload] => // NOOP
    }
  }

  private[this] implicit class VertexPimper(v1: PayloadVertex[TestRunContext, TestPayload]) {
    def call(implicit runContext: TestRunContext) =
      runContext.calls(v1.payload)

    def enter(implicit runContext: TestRunContext) =
      call.enter

    def exit(implicit runContext: TestRunContext) =
      call.exit

    def ranConcurrentlyWith(v2: PayloadVertex[TestRunContext, TestPayload])(implicit rc: TestRunContext) =
      ( ! ( v1.enter <= v2.exit ) || ( v1.exit >= v2.enter ) ) && ( ! ( v2.enter <= v1.exit ) || ( v2.exit >= v1.enter ) )

    def shouldNotHaveRunConcurrentlyWith(v2: PayloadVertex[TestRunContext, TestPayload])(implicit rc: TestRunContext) = {
      if ( v1 ranConcurrentlyWith v2 )
        fail(s"$v1 should not have run concurrently with $v2 (${v1.enter}-${v1.exit} ${v2.enter}-${v2.exit})")
    }

    def shouldHaveRunConcurrentlyWith(v2: PayloadVertex[TestRunContext, TestPayload])(implicit rc: TestRunContext) = {
      if ( ! ( v1 ranConcurrentlyWith v2 ) )
        fail(s"$v1 should have run concurrently with $v2 (${v1.enter}-${v1.exit} ${v2.enter}-${v2.exit})")
    }

    def shouldPrecede(v2: PayloadVertex[TestRunContext, TestPayload])(implicit rc: TestRunContext) = {
      v1.exit should be <= v2.enter
    }
  }

  it ("should run a vertex") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))

    g += va

    implicit val runContext = new TestRunContext

    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)
    va.call.run shouldBe true
  }

  it ("should run independent vertices concurrently") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))

    g += va
    g += vb

    implicit val runContext = new TestRunContext

    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)
    va shouldHaveRunConcurrentlyWith vb

    va.call.run shouldBe true
    vb.call.run shouldBe true
  }

  it ("should be able to run the same graph multiple times") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))

    g += va

    val runContext1 = new TestRunContext
    Await.result(g.run(runContext1), Duration.Inf)
    assertAllNodesVisited(g)(runContext1)
    va.call(runContext1).run shouldBe true

    val runContext2 = new TestRunContext
    Await.result(g.run(runContext2), Duration.Inf)
    assertAllNodesVisited(g)(runContext2)
    va.call(runContext2).run shouldBe true
  }

  it ("should be able to run the same graph multiple times concurrently") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))

    g += va

    val runContext1 = new TestRunContext
    val future1 = g.run(runContext1)

    val runContext2 = new TestRunContext
    val future2 = g.run(runContext2)

    Await.result(Future.sequence(Iterable(future1, future2)), Duration.Inf)

    assertAllNodesVisited(g)(runContext1)
    va.call(runContext1).run shouldBe true

    assertAllNodesVisited(g)(runContext2)
    va.call(runContext2).run shouldBe true

    va.call(runContext1).enter should be < va.call(runContext2).exit
    va.call(runContext2).enter should be < va.call(runContext1).exit
  }

  it ("should run dependent vertices in strict order") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))

    g += Edge(va, vb)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    va.call.run shouldBe true
    vb.call.run shouldBe true
  }

  it ("should abort downstream vertices on throw") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a", throws = true))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))

    g += Edge(va, vb)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    va.call.run shouldBe true
    vb.call.run shouldBe false
  }

  it ("should abort downstream vertices transitively on throw") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a", throws = true))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))

    g += Edge(va, vb)
    g += Edge(vb, vc)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    vb shouldPrecede vc

    va.call.run shouldBe true
    vb.call.run shouldBe false
    vc.call.run shouldBe false
  }

  it ("should abort downstream vertices on false") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a", returns = false))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))

    g += Edge(va, vb)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    va.call.run shouldBe true
    vb.call.run shouldBe false
  }

  it ("should abort downstream vertices transitively on false") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a", returns = false))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))

    g += Edge(va, vb)
    g += Edge(vb, vc)

    implicit val runContext = new TestRunContext
    Await.result(g.run(runContext), Duration.Inf)

    assertAllNodesVisited(g)

    va shouldPrecede vb
    vb shouldPrecede vc

    va.call.run shouldBe true
    vb.call.run shouldBe false
    vc.call.run shouldBe false
  }

  it ("should handle vertices with multiple in-edges") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))
    val vd = Vertex[TestRunContext, TestPayload](new TestPayload("d"))

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
    vb shouldPrecede vd
    vc shouldPrecede vd
    vb shouldHaveRunConcurrentlyWith vc

    va.call.run shouldBe true
    vb.call.run shouldBe true
    vc.call.run shouldBe true
    vd.call.run shouldBe true
  }

  it ("should abort vertices with multiple unresolved in-edges immediately") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b", throws = true))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))
    val vd = Vertex[TestRunContext, TestPayload](new TestPayload("d"))

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

    va.call.run shouldBe true
    vb.call.run shouldBe true
    vc.call.run shouldBe true
    vd.call.run shouldBe false
  }

  it ("should use semaphores properly") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))
    val vd = Vertex[TestRunContext, TestPayload](new TestPayload("d"))
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

    va.call.run shouldBe true
    vb.call.run shouldBe true
    vc.call.run shouldBe true
    vd.call.run shouldBe true

    va shouldPrecede vb
    va shouldPrecede vc
    va shouldPrecede vd


    // The three nodes that are tied to the semaphore should not run concurrently

    vb shouldNotHaveRunConcurrentlyWith vc
    vc shouldNotHaveRunConcurrentlyWith vd
    vb shouldNotHaveRunConcurrentlyWith vd
  }

  it ("should detect no cycle") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))

    g += Edge(va, vb)
    g += Edge(vb, vc)

    g.findCycle(va) shouldBe None
  }

  it ("should detect single-vertex cycle") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))

    g += Edge(va, va)

    g.findCycle(va) shouldBe Some(List(va))
  }

  it ("should detect multiple-vertex cycle") {
    var g = new RunnableGraph[TestRunContext, TestPayload]

    val va = Vertex[TestRunContext, TestPayload](new TestPayload("a"))
    val vb = Vertex[TestRunContext, TestPayload](new TestPayload("b"))
    val vc = Vertex[TestRunContext, TestPayload](new TestPayload("c"))

    g += Edge(va, vb)
    g += Edge(vb, vc)
    g += Edge(vc, va)

    g.findCycle(va) shouldBe Some(List(va, vb, vc))
  }
}
