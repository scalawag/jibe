package org.scalawag.jibe.executive

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.backend.Commander
import org.scalawag.jibe.executive.PlanGraphFactory.{LeafVertex, VisitContext, VisitListener2}
import org.scalawag.jibe.multitree.{Mandate, MandateSequence, MandateSet, MultiTreeLeaf}
import org.scalawag.jibe.report.Report
import org.scalawag.jibe.report.Report.{SKIPPED, Status}

import scala.util.Success

class PlanGraphFactoryTest extends FunSpec with Matchers with MockFactory {

  trait VisitListenerCallType
  case object Enter extends VisitListenerCallType
  case object Exit extends VisitListenerCallType
  case object Bypass extends VisitListenerCallType

  case class VisitListenerCall(tipe: VisitListenerCallType, vertex: LeafVertex, status: Report.Status) {
    val timestamp: Long = System.currentTimeMillis()
  }

  class TestVisitListener extends VisitListener2 {
    var calls = Seq.empty[VisitListenerCall]

    override def enter(vertex: LeafVertex, status: Status) = calls :+= VisitListenerCall(Enter, vertex, status)
    override def exit(vertex: LeafVertex, status: Status) = calls :+= VisitListenerCall(Exit, vertex, status)
    override def bypass(vertex: LeafVertex, status: Status) = calls :+= VisitListenerCall(Bypass, vertex, status)
  }

  describe("LeafVertex") {
    it("should change state immediately if one Abort signal is received") {
      import PlanGraphFactory._
      val commander = mock[Commander]

      val m = mock[Mandate]

      val l1 = MultiTreeLeaf(m)
      val l2 = MultiTreeLeaf(m)
      val l3 = MultiTreeLeaf(m)

      val v1 = LeafVertex(l1, commander)
      val v2 = LeafVertex(l2, commander)
      val v3 = LeafVertex(l3, commander)

      val e1 = Edge(v1, v3)
      val e2 = Edge(v2, v3)

      e1.signal(Success(MultiTreeVertex.Aborted(SKIPPED)))

//      lis
//      v1.state shouldBe MultiTreeVertex.Aborted

//      val listener = new TestVisitListener
//      val ctxt = VisitContext(true, listener, TestLoggerFactory)

      //      Await.result(plan.runnableGraph.run(ctxt)(ExecutionContext.global), Duration.Inf)

//      listener.calls.foreach(println)
    }
  }

}
