package org.scalawag.jibe.backend

import scala.annotation.tailrec
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge

object Orderer {

  private[this] def getAtomicMandates(mandates: Iterable[Mandate]): Iterable[Mandate] = {
    @tailrec
    def helper(input: Iterable[Mandate], output: Iterable[Mandate] = Iterable.empty): Iterable[Mandate] =
      input.headOption match {
        case Some(CompositeMandate(ms@_*)) =>
          helper(ms ++ input.tail, output)
        case Some(m) =>
          helper(input.tail, output ++ Iterable(m))
        case None =>
          output
    }

    helper(mandates)
  }

  def order(mandates: Iterable[Mandate]): Iterable[Mandate] = {
    var graph = Graph[Mandate, DiEdge]()

    // Add all of our Mandates into the graph as nodes.  CompositeMandates are entered individually.

    def addNodesToGraph(mandate: Mandate): Unit =
      mandate match {
        case CompositeMandate(mandates@_*) =>
          mandates.foreach(addNodesToGraph)
        case _ =>
          graph += mandate
      }

    mandates.foreach(addNodesToGraph)

    // Add explicit orderings (from "before" calls)

    def addExplicitEdgesToGraph(mandate: Mandate): Unit =
      mandate match {
        case CompositeMandate(mandates@_*) =>
          mandates.foreach(addExplicitEdgesToGraph)
          if ( mandates.size > 1 )
            mandates.sliding(2).foreach { pair =>
              graph += DiEdge(pair(0), pair(1))
            }
        case _ =>
          // NOOP
      }

    mandates.foreach(addExplicitEdgesToGraph)

    // Add implicit orderings (from resource dependencies)

    val all = getAtomicMandates(mandates)

    val byPrerequisites = all.flatMap { m =>
      m.prerequisites.map((_, m))
    } groupBy { case (p, m) =>
      p
    } mapValues { m =>
      m.map(_._2)
    }

    val byConsequences = all.flatMap { m =>
      m.consequences.map((_, m))
    } groupBy { case (p, m) =>
      p
    } mapValues { m =>
      m.map(_._2)
    }

    byPrerequisites foreach { case (resource, afters) =>
      byConsequences.get(resource) foreach { befores =>
        befores foreach { before =>
          afters foreach { after =>
            graph += DiEdge(before, after)
          }
        }
      }
    }

    val r = graph.topologicalSort.right.get.toOuterNodes.toIterable

    r
  }
}
