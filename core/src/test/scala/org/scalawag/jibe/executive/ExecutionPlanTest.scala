package org.scalawag.jibe.executive

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.{Logging, TestLogging}
import org.scalawag.jibe.executive.PlanGraphFactory.{LeafVertex, LoggerFactory, VisitContext, VisitListener}
import org.scalawag.jibe.multitree._
import org.scalawag.jibe.report.Report
import org.scalawag.jibe.report.Report.{SUCCESS, Status, UNNEEDED}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class ExecutionPlanTest  extends FunSpec with Matchers {
  TestLogging

  class TestMandate(name: String) extends StatelessMandate {
    override val label = name
    override val fingerprint = name
    override def takeAction(implicit context: MandateExecutionContext) = {}
    override val toString = name
  }

  trait VisitListenerCallType
  case object Enter extends VisitListenerCallType
  case object Exit extends VisitListenerCallType
  case object Bypass extends VisitListenerCallType

  case class VisitListenerCall(tipe: VisitListenerCallType, vertex: LeafVertex, status: Report.Status) {
    val timestamp: Long = System.currentTimeMillis()
  }

  class TestVisitListener extends VisitListener {
    var calls = Seq.empty[VisitListenerCall]

    override def enter(vertex: LeafVertex, status: Status) = calls :+= VisitListenerCall(Enter, vertex, status)
    override def exit(vertex: LeafVertex, status: Status) = calls :+= VisitListenerCall(Exit, vertex, status)
    override def bypass(vertex: LeafVertex, status: Status) = calls :+= VisitListenerCall(Bypass, vertex, status)
  }

  object TestLoggerFactory extends LoggerFactory {
    override def getLogger(vertex: LeafVertex) = Logging.log
  }

  it("should bypass branches with shared leaves") {
    val f = MultiTreeLeaf(new TestMandate("f"))
    val a1 = MultiTreeLeaf(new TestMandate("a1"))
    val a2 = MultiTreeLeaf(new TestMandate("a2"))
    val b = MultiTreeLeaf(new TestMandate("b"))
    val c1 = MultiTreeLeaf(new TestMandate("c1"))
    val c2 = MultiTreeLeaf(new TestMandate("c2"))

    val flag = new Flag()

    val tree = MandateSet(
      f.add(FlagOn(flag, SUCCESS)),
      MandateSet(
        a1,
        b,
        c1
      ).add(IfFlagged(flag)),
      MandateSet(
        a2,
        b,
        c2
      )
    )

    val plan = new ExecutionPlan(Seq(CommanderMultiTree(null, tree)))
    val listener = new TestVisitListener
    val ctxt = VisitContext(true, listener, TestLoggerFactory)
    Await.result(plan.runnableGraph.run(ctxt)(ExecutionContext.global), Duration.Inf)

    listener.calls.foreach(println)
  }

  it("should bypass branches with shared leaves (UNNEEDED)") {
    val f = MultiTreeLeaf(new TestMandate("f"))
    val a1 = MultiTreeLeaf(new TestMandate("a1"))
    val a2 = MultiTreeLeaf(new TestMandate("a2"))
    val b = MultiTreeLeaf(new TestMandate("b"))
    val c1 = MultiTreeLeaf(new TestMandate("c1"))
    val c2 = MultiTreeLeaf(new TestMandate("c2"))

    val flag = new Flag()

    val tree = MandateSet(
      f.add(FlagOn(flag, UNNEEDED)),
      MandateSet(
        a1,
        b,
        c1
      ).add(IfFlagged(flag)),
      MandateSet(
        a2,
        b,
        c2
      )
    )

    val plan = new ExecutionPlan(Seq(CommanderMultiTree(null, tree)))
    val listener = new TestVisitListener
    val ctxt = VisitContext(true, listener, TestLoggerFactory)
    Await.result(plan.runnableGraph.run(ctxt)(ExecutionContext.global), Duration.Inf)

    listener.calls.foreach(println)
  }
}
