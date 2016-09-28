package org.scalawag.jibe.backend

import java.io.PrintWriter

import org.scalawag.jibe.AbortException
import org.scalawag.jibe.mandate.{CheckableCompositeMandate, CheckableMandate, CompositeMandate, Mandate}

import scalax.collection.Graph
import scalax.collection.edge.LDiEdge
import org.scalawag.jibe.Logging._

object Orderer {

  def order(mandate: CheckableMandate): CheckableMandate = {
    log.debug(s"ordering mandate: $mandate")
    mandate match {
      case CheckableCompositeMandate(desc, innards, fixedOrder) =>

        var graph = Graph[Mandate, LDiEdge]()

        // Add all of our inner mandates into the graph as nodes.

        innards.foreach { m =>
          graph += m
        }

        // Add explicit orderings, if this CompositeMandate has fixed-order innards.
        // You could argue that we don't need to order in this situation, but it gives the system a chance to fail
        // if the fixed ordering interacts with the implicit ordering to produce a conflict/cycle.

        if ( fixedOrder && innards.size > 1 ) {
          innards.sliding(2).foreach { pair =>
            graph += LDiEdge(pair(0), pair(1))("fixed-ordering")
          }
        }

        // Add implicit orderings (from resource dependencies)

        val byPrerequisites = innards.flatMap { m =>
          m.prerequisites.map((_, m))
        } groupBy { case (p, m) =>
          p
        } mapValues { m =>
          m.map(_._2)
        }

        val byConsequences = innards.flatMap { m =>
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
                if ( before != after )
                  graph += LDiEdge(before, after)(resource)
              }
            }
          }
        }

        log.debug { pw: PrintWriter =>
          pw.println("graph edges")
          graph.edges.toOuter.foreach( e => pw.println(s"  $e"))
        }

        // Workaround for the fact that scala-graph is just returning a topo sort that leaves out the nodes which
        // are involved in cycles, instead of giving me a Left with the cycle info.

        val cycle = graph.findCycle foreach { cycle =>
          System.err.println(s"Detected cycle within mandate graph at $mandate")
          cycle foreach { t =>
            System.err.println(s"  $t")
          }
          throw new AbortException()
        }

        val ts = graph.topologicalSort

        ts match {
          case Right(path) =>
            log.debug { pw: PrintWriter =>
              pw.println("topological sort order:")
              path.toOuterNodes.asInstanceOf[Traversable[Mandate]].foreach { e => pw.println(s"  $e") }
            }

            new CheckableCompositeMandate(desc, path.toOuterNodes.toSeq.map(_.asInstanceOf[CheckableMandate]).map(order))
          case Left(cycle) =>
            innards foreach { m =>
              println(s"MANDATE: ${m.description}")
              println(s"PREREQUISITES: ${m.prerequisites.mkString(",")}")
              println(s"CONSEQUENCES: ${m.consequences.mkString(",")}")
            }
            println(cycle)

            println("=====")
            cycle.outerEdgeTraverser.foreach { e =>
              println(e.head.description)
              println(e.label)
              println(e.last.description)
            }

            println("=====")
//            graph.findCycle.foreach( c => c.foreach { i =>
//              if ( i.isN)
//              case n: graph#NodeBase =>
//
//              case e: EdgeBase =>
//                println(e.getClass.getName)
//                println(e)
//            })

            throw new RuntimeException(s"cycle detected: $cycle")
        }

      case m =>
        // No inner mandates (not a composite), so there's no ordering to do...
        m
    }
  }

}
