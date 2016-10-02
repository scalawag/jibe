package org.scalawag.jibe.backend

import java.io.File

import org.scalawag.jibe.FileUtils
import org.scalawag.jibe.report.ExecutiveStatus
import scalax.collection.Graph
import scalax.collection.edge.LDiEdge

object Executive {
  def execute(rootJob: MandateJob): Unit = {

    sealed trait Vertex
    case class ParentStart(parent: ParentMandateJob) extends Vertex {
      override val toString = s"IN-${parent.mandate.description.getOrElse(parent.mandate.toString)}"
    }
    case class ParentEnd(parent: ParentMandateJob) extends Vertex {
      override val toString = s"OUT-${parent.mandate.description.getOrElse(parent.mandate.toString)}"
    }
    case class ResourceVertex(resource: Resource) extends Vertex {
      override val toString = resource.toString
    }
    case class Leaf(job: LeafMandateJob) extends Vertex {
      override val toString = job.mandate.description.getOrElse("?")
    }

    var graph = Graph.empty[Vertex, LDiEdge]

    def addToGraph(job: MandateJob): (Vertex, Vertex) =
      job match {
        case j: LeafMandateJob =>
          val vertex = Leaf(j)
          graph += vertex

          // Add resource edges
          j.mandate.consequences foreach { r =>
            graph += LDiEdge(vertex, ResourceVertex(r))("")
          }

          j.mandate.prerequisites foreach { r =>
            graph += LDiEdge(ResourceVertex(r), vertex)("")
          }

          (vertex, vertex)

        case j: ParentMandateJob =>
          val in = ParentStart(j)
          val out = ParentEnd(j)

          val vertices = j.children.map(addToGraph)

          // Add all children, add edges to make children execution in series (sequence) or parallel (set).
          j.mandate match {
            case seq: MandateSequence =>
              // Sequence all children
              vertices.sliding(2) foreach { case childPairInAndOut =>
                val lout = childPairInAndOut(0)._2
                val rin = childPairInAndOut(1)._1
                graph += LDiEdge(lout, rin)("")
              }

              // Connect the in and out
              graph += LDiEdge(in, vertices.head._1)("")
              graph += LDiEdge(vertices.last._2, out)("")

            case set: MandateSet =>
              // Connect in and out to each child in parallel
              vertices foreach { case childInAndOut =>
                graph += LDiEdge(in, childInAndOut._1)("")
                graph += LDiEdge(childInAndOut._2, out)("")
              }

            case set: CommanderMandate => // TODO: figure out how to generalize this to another type
              // Connect in and out to each child in parallel
              vertices foreach { case childInAndOut =>
                graph += LDiEdge(in, childInAndOut._1)("")
                graph += LDiEdge(childInAndOut._2, out)("")
              }

            case set: RunMandate => // TODO: figure out how to generalize this to another type
              // Connect in and out to each child in parallel
              vertices foreach { case childInAndOut =>
                graph += LDiEdge(in, childInAndOut._1)("")
                graph += LDiEdge(childInAndOut._2, out)("")
              }
          }

          // Return the in and out so that our caller can wire it up to their children.
          (in, out)
      }

    val (startVertex, _) = addToGraph(rootJob)

    {
      import scalax.collection.io.dot._

      val rg =
      DotRootGraph(
        directed  = true,
        id        = Some(Id("MyDot")),
        attrList = Seq(DotAttr(Id("rankdir"), Id("LR")))
      )

      def edgeTransformer(innerEdge: Graph[Vertex,LDiEdge]#EdgeT):
      Option[(DotGraph,DotEdgeStmt)] = innerEdge.edge match {
        case LDiEdge(source, target, label) => label match {
          case label: String =>
            Some((rg,
              DotEdgeStmt(
                NodeId(source.toString),
                NodeId(target.toString),
                if (label.nonEmpty) List(DotAttr(Id("label"), Id(label.toString))) else                Nil)))
        }}

      val dot = graph.toDot(rg, edgeTransformer _)

      FileUtils.writeFileWithPrintWriter(new File("./graph.dot")) { pw => pw.println(dot) }
    }

    // Issue warnings about unmanaged resources.
    val g = graph
    g.nodes foreach { n: g.NodeT =>
      n.value match {
        case ResourceVertex(r) if n.inDegree == 0 =>
          println(s"WARNING: resource $r is required but not produced by any mandates")
        case _ => // NOOP
      }
    }

    // Fail now if there are cycles in the graph

    graph.findCycle foreach { cycle =>
      System.err.println(s"Detected cycle within mandate graph:")
      cycle foreach { t =>
        System.err.println(s"  $t")
      }
    }

    // Actually perform the mandate executions. Ensure that we visit all leaves only after their predecessors have
    // been visited by performing a topological sort.

    g.topologicalSort.right.get foreach { case node =>
      node.value match {
        case leaf: Leaf =>
          val job = leaf.job
          if ( job.executiveStatus.isEmpty ) {
            val status = job.go()
            if ( status.executiveStatus == Some(ExecutiveStatus.FAILURE) ) {
              // process all successor jobs to the failed job
              g.outerNodeTraverser(node).foreach {
                case l: Leaf if l != leaf => l.job.executiveStatus = ExecutiveStatus.BLOCKED // abort leaf jobs
                case x => // ignore everything but leaf jobs
              }
            }
          }

        case _ => // Ignore everything but leaf jobs
      }
    }

//    graph.topologicalSort.right.get.toOuter.collect { case Leaf(x) => x }.collect { case l: LeafMandateJob => l }.toIterable
  }
}
