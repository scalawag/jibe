package org.scalawag.jibe.backend

import java.io.{File, PrintWriter}

import org.scalawag.jibe.mandate._
import org.scalawag.jibe.{AbortException, FileUtils, Logging}
import org.scalawag.jibe.report.ExecutiveStatus

import scala.annotation.tailrec
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge

object Executive {
  private[this] sealed trait Vertex
  private[this] sealed trait CycleParticipant extends Vertex
  private[this] case class ParentStart(parent: ParentMandateJob) extends Vertex {
    override val toString = s"IN-${parent.mandate.description.getOrElse(parent.hashCode)}"
  }
  private[this] case class ParentEnd(parent: ParentMandateJob) extends Vertex {
    override val toString = s"OUT-${parent.mandate.description.getOrElse(parent.hashCode)}"
  }
  private[this] case class ResourceVertex(resource: Resource) extends CycleParticipant {
    override val toString = resource.toString
  }
  private[this] case class Leaf(job: LeafMandateJob) extends CycleParticipant {
    override val toString = job.mandate.description.getOrElse("?")
  }
  private[this] case class SequenceOrdering(mandate: MandateSequence, index: Int) extends CycleParticipant {
    override val toString = s"${mandate.hashCode} - $index"
  }

  private[this] def buildGraph(rootJob: MandateJob) = {
    var graph = Graph.empty[Vertex, DiEdge]

    def addToGraph(job: MandateJob): (Vertex, Vertex) =
      job match {
        case j: LeafMandateJob =>
          val vertex = Leaf(j)
          graph += vertex

          // Add resource edges
          j.mandate.consequences foreach { r =>
            graph += DiEdge(vertex, ResourceVertex(r))
          }

          j.mandate.prerequisites foreach { r =>
            graph += DiEdge(ResourceVertex(r), vertex)
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
              vertices.sliding(2).zipWithIndex foreach { case (childPairInAndOut, n) =>
                val lout = childPairInAndOut(0)._2
                val seqVertex = SequenceOrdering(seq, n)
                val rin = childPairInAndOut(1)._1
                graph += DiEdge(lout, seqVertex)
                graph += DiEdge(seqVertex, rin)
              }

              // Connect the in and out
              graph += DiEdge(in, vertices.head._1)
              graph += DiEdge(vertices.last._2, out)

            case set: MandateSet =>
              // Connect in and out to each child in parallel
              vertices foreach { case childInAndOut =>
                graph += DiEdge(in, childInAndOut._1)
                graph += DiEdge(childInAndOut._2, out)
              }

            case set: CommanderMandate => // TODO: figure out how to generalize this to another type
              // Connect in and out to each child in parallel
              vertices foreach { case childInAndOut =>
                graph += DiEdge(in, childInAndOut._1)
                graph += DiEdge(childInAndOut._2, out)
              }

            case set: RunMandate => // TODO: figure out how to generalize this to another type
              // Connect in and out to each child in parallel
              vertices foreach { case childInAndOut =>
                graph += DiEdge(in, childInAndOut._1)
                graph += DiEdge(childInAndOut._2, out)
              }
          }

          // Return the in and out so that our caller can wire it up to their children.
          (in, out)
      }

    addToGraph(rootJob)

    graph
  }

  def dumpGraph(rootJob: MandateJob, out: File): Unit = {
    import scalax.collection.io.dot._

    val graph = buildGraph(rootJob)

    val rg = DotRootGraph(
      directed  = true,
      id        = Some(Id("MyDot")),
      attrList = Seq(DotAttr(Id("rankdir"), Id("LR")))
    )

    def edgeTransformer(innerEdge: Graph[Vertex,DiEdge]#EdgeT):
    Option[(DotGraph,DotEdgeStmt)] = innerEdge.edge match {
      case DiEdge(source, target) =>
        Some((rg,
          DotEdgeStmt(
            NodeId(source.toString),
            NodeId(target.toString))))
    }

    val dot = graph.toDot(rg, edgeTransformer _)

    FileUtils.writeFileWithPrintWriter(out) { pw => pw.print(dot) }
  }

  def execute(rootJob: MandateJob): Unit = {
    val graph = buildGraph(rootJob)

    // Issue warnings about unmanaged resources.
    graph.nodes foreach { n: graph.NodeT =>
      n.value match {
        case ResourceVertex(r) if n.inDegree == 0 =>
          println(s"WARNING: resource $r is required but not produced by any mandates")
        case _ => // NOOP
      }
    }

    // Fail now if there are cycles in the graph
    // Breaks up a cycle into segments that each start and end with a Leaf (mandate) and have no mandates between them.

    @tailrec
    def segmentize(todo: Iterable[CycleParticipant], answer: List[List[CycleParticipant]] = Nil, first: Option[CycleParticipant] = None): List[List[CycleParticipant]] =
      todo match {
        case Nil =>
          answer
        case (head: Leaf) :: Nil =>
          answer
        case (head: Leaf) :: tail =>
          // head is a leaf, look for the next one
          val nonLeaves = tail.takeWhile(! _.isInstanceOf[Leaf])
          val newTail = tail.dropWhile(! _.isInstanceOf[Leaf])
          segmentize(newTail, answer ++ List( ( head :: nonLeaves ) ++ List(newTail.headOption.getOrElse(first.get)) ), Some(first.getOrElse(head)) )
        case head :: tail =>
          // head is not a leaf... rotate until it is
          segmentize(tail ++ List(head), answer, first)
      }

    def pathToSegments(nodes: Traversable[graph.NodeT]) = {
      val interesting = nodes.toOuterNodes.collect { case cp: CycleParticipant => cp }.toList
      val segments = segmentize(interesting)

      Logging.log.error { pw: PrintWriter =>
        pw.println("Raw cycle output:")
        pw.println("-" * 80)
        interesting.foreach(pw.println)
        pw.println("-" * 80)
        pw.println("Cycle segments:")
        pw.println("-" * 80)
        segments foreach { s =>
          s.foreach(pw.println)
          pw.println("-" * 80)
        }
      }

      segments
    }

    def segmentsToLines(segments: List[List[CycleParticipant]]): List[String] =
      segments map { s =>
        val from = s.head
        val to = s.last
        val through = s(1)

        (through: @unchecked) match {
          case SequenceOrdering(mandate, _) =>
            s"""  "$from" must precede "$to" due to a mandate sequence${mandate.description.map(d => s""" named "$d"""").getOrElse("")}"""
          case ResourceVertex(resource) =>
            s"""  "$from" must precede "$to" due to $resource"""
        }
      }

    graph.findCycle foreach { cycle =>
      System.err.println(s"ERROR: Detected cycle within mandate graph:")
      val segments = pathToSegments(cycle.nodes)
      segmentsToLines(segments).foreach(System.err.println)
      throw new AbortException
    }

    // Actually perform the mandate executions. Ensure that we visit all leaves only after their predecessors have
    // been visited by performing a topological sort.

    graph.topologicalSort.right.get foreach { case node =>
      node.value match {
        case leaf: Leaf =>
          val job = leaf.job
          if ( job.executiveStatus == ExecutiveStatus.PENDING ) {
            val status = job.go()
            if ( status.executiveStatus == ExecutiveStatus.FAILURE ) {
              // process all successor jobs to the failed job
              graph.innerNodeTraverser(node).foreach { case successor =>
                successor.value match {
                  case l: Leaf if l != leaf => // abort leaf jobs
                    // mark it as blocked
                    l.job.executiveStatus = ExecutiveStatus.BLOCKED
                    // log (in that job) why it was blocked
                    l.job.log.info { pw: PrintWriter =>
                      pw.println(s"""blocked by failure of mandate "${job.mandate.description.getOrElse(job.mandate.toString)}"""")
                      val segments = pathToSegments(( node shortestPathTo successor ).get.nodes)
                      segmentsToLines(segments) foreach pw.println
                    }
                  case x => // ignore everything but leaf jobs
                }
              }
            }
          }

        case _ => // Ignore everything but leaf jobs
      }
    }
  }
}
