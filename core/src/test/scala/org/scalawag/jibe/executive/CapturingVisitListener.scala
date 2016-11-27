package org.scalawag.jibe.executive

import PlanGraphFactory._
import org.scalawag.jibe.executive.CapturingVisitListener.{OnEdge, OnVertex, VisitListenerCall}

object CapturingVisitListener {
  trait VisitListenerCall {
    val timestamp: Long = System.currentTimeMillis()
  }
  case class OnVertex[V <: Vertex](v: V, signals: Iterable[Option[V#SignalType]], oldState: Option[V#StateType], newState: Option[V#StateType]) extends VisitListenerCall {
    override val toString = s"v: $signals -> $v => $oldState -> $newState"
  }
  case class OnEdge[E <: Edge](e: E, signal: E#ToType#SignalType) extends VisitListenerCall {
    override val toString = s"e: $e $signal"
  }
}

class CapturingVisitListener extends VisitListener {
  var calls = Seq.empty[VisitListenerCall]

  override def onVertex[V <: Vertex](v: V, signals: Iterable[Option[V#SignalType]], oldState: Option[V#StateType], newState: Option[V#StateType]) = {
    calls :+= OnVertex(v, signals, oldState, newState)
  }

  override def onEdge[E <: Edge](e: E, signal: E#ToType#SignalType) = {
    calls :+= OnEdge(e, signal)
  }
}
