package org.scalawag.jibe.executive

import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.RunnableGraph._
import org.scalawag.jibe.backend.{Commander, RunnableGraph}
import org.scalawag.jibe.multitree.MultiTreeBranch.{Parallel, Series}
import org.scalawag.jibe.multitree._
import org.scalawag.jibe.report.JsonFormats._
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report._
import org.scalawag.jibe.AbortException
import spray.json._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

// Instantiation of this class causes the plan to be built from its commander-specific multi-trees.

class ExecutionPlan(commanderMultiTrees: Seq[CommanderMultiTree]) {

  // Generate the MultiTree IDs that we'll need for the rest of the logic in here.  The IDs are unique within a
  // Commander-specific MultiTree, so this is really a map of maps, one for each commander.

  private[this] val multiTreeIdMap = commanderMultiTrees.map { cm =>
    cm.commander -> new MultiTreeIdMap(cm.multiTree)
  }.toMap

  // This is the graph that we're going to populate to represent this MultiTree and execute it.

  private[this] var graph = new RunnableGraph[RunContext, Payload]

  // Subgraphs just for convenience when building up a large graph out of smaller graphs.  Using the edge method,
  // we'll create edges between two subgraphs and the tail of the first to the head of the second will be connected.

  private[this] case class Subgraph(head: PayloadVertex[RunContext, Payload], tail: PayloadVertex[RunContext, Payload])

  // A map to hold things that can be scoped by-commander or globally.  Consumers pass in the Commander during
  // getOrCreate calls so that it can be used if necessary.  If the Scoped item is globally scoped, the passed-in
  // Commander is ignored.  If the item does not already exist in the map, it is created using the callers createFn.

  private[this] class ScopedMap[A <: Scoped, B] {
    private[this] var map = Map.empty[(A, Option[Commander]), B]

    def getOrCreate(a: A, commander: Commander)(createFn: => B) = {
      val key = a.scope match {
        case GlobalScope => (a, None)
        case CommanderScope => (a, Some(commander))
      }

      map.get(key).getOrElse {
        val b = createFn
        map += ( key -> b )
        b
      }
    }
  }

  // A map to hold things that are always scoped by-commander.  This is similar to ScopedMap above except that the
  // items it holds can not be globally-scoped.

  private[this] class CommanderMap[A, B] {
    private[this] var map = Map.empty[(A, Commander), B]

    def getOrCreate(a: A, commander: Commander)(fn: => B) = {
      val key = (a, commander)
      map.get(key).getOrElse {
        val b = fn
        map += ( key -> b )
        b
      }
    }
  }

  // These maps are here to manage the parts of the graph that we've already created.  We may need them again when
  // we find another path to the same item.

  private[this] val plannedResources = new ScopedMap[Resource, PayloadVertex[RunContext, Payload]]
  private[this] val plannedBarriers = new ScopedMap[Barrier, PayloadVertex[RunContext, Payload]]
  private[this] val plannedSemaphores = new ScopedMap[Semaphore, SemaphoreVertex[RunContext, Payload]]
  private[this] val plannedBranches = new CommanderMap[MultiTreeId, Subgraph]
  private[this] val plannedLeaves = new CommanderMap[MultiTreeId, Subgraph]

  // Methods to create the various vertices in the graph.  These are mostly just calls to the maps above with the
  // createFn specified.

  private[this] def planSemaphore(c: Commander, s: Semaphore) = plannedSemaphores.getOrCreate(s, c) {
    RunnableGraph.SemaphoreVertex[RunContext, Payload](s.count)
  }

  private[this] def planResource(c: Commander, r: Resource) = plannedResources.getOrCreate(r, c) {
    RunnableGraph.PayloadVertex[RunContext, Payload](ResourcePayload(r))
  }

  private[this] def planBarrier(c: Commander, b: Barrier) = plannedBarriers.getOrCreate(b, c) {
    RunnableGraph.PayloadVertex[RunContext, Payload](BarrierPayload(b))
  }

  private[this] def planLeaf(commander: Commander, leaf: MultiTreeLeaf) = {
    val id = multiTreeIdMap(commander).getId(leaf)
    plannedLeaves.getOrCreate(id, commander) {
      val t = Vertex[RunContext, Payload](Leaf(leaf, commander))
      val sg = Subgraph(t, t)
      planDecorations(commander, sg, leaf.decorations)
      sg
    }
  }

  private[this] def planBranch(commander: Commander, branch: MultiTreeBranch) = {
    val id = multiTreeIdMap(commander).getId(branch)
    plannedBranches.getOrCreate(id, commander) {
      // Create the in and out vertices for this branch.

      val in = Vertex[RunContext, Payload](BranchHead(branch))
      val out = Vertex[RunContext, Payload](BranchTail(branch))

      // recursively plan all of the contents

      val childSubgraphs = branch.contents.map(planMultiTree(commander, _))

      branch.order match {
        case Series =>
          // Sequence the children

          if ( childSubgraphs.length > 1 ) {
            childSubgraphs.toList.sliding(2) foreach { case List(l, r) =>
              val seq = Vertex[RunContext, Payload](Sequencer(branch, commander))
              graph += Edge(l.tail, seq)
              graph += Edge(seq, r.head)
            }
          }

          // Wire the first and last children to our branch in and out.

          childSubgraphs.headOption foreach { c =>
            graph += Edge(in, c.head)
          }

          childSubgraphs.lastOption foreach { c =>
            graph += Edge(c.tail, out)
          }

        case Parallel =>
          // Connect branch in and out to each child in parallel

          childSubgraphs foreach { c =>
            graph += Edge(in, c.head)
            graph += Edge(c.tail, out)
          }
      }

      val sg = Subgraph(in, out)
      planDecorations(commander, sg, branch.decorations)
      sg
    }
  }

  private[this] def planMultiTree(commander: Commander, multiTree: MultiTree): Subgraph = multiTree match {
    case l: MultiTreeLeaf => planLeaf(commander, l)
    case b: MultiTreeBranch => planBranch(commander, b)
  }

  // Adds the edges needed to realize the MultiTreeDecorations for any MultiTree.

  private[this] def planDecorations(commander: Commander, subgraph: Subgraph, decorations: Set[MultiTreeDecoration]) =
    decorations foreach {
      case Consequences(rs) =>
        rs foreach { r =>
          graph += Edge(subgraph.tail, planResource(commander, r))
        }

      case Prerequisites(rs) =>
        rs foreach { r =>
          graph += Edge(planResource(commander, r), subgraph.head)
        }

      case EnterCriticalSection(s) =>
        graph += Edge(planSemaphore(commander, s), subgraph.head)

      case ExitCriticalSection(s) =>
        graph += Edge(subgraph.tail, planSemaphore(commander, s))

      case CriticalSection(semaphore) =>
        val s = planSemaphore(commander, semaphore)
        graph += Edge(s, subgraph.head)
        graph += Edge(subgraph.tail, s)

      case BeforeBarrier(b) =>
        graph += Edge(subgraph.tail, planBarrier(commander, b))

      case AfterBarrier(b) =>
        graph += Edge(planBarrier(commander, b), subgraph.head)

      // TODO: these need to be implemented
      case ActivateWhenCompleteIfStatusIs(activator, status) => ???
      case IfActivated(activator) => ???
      case IfNotActivated(activator) => ???
    }


  private[this] def planCommanderMultiTree(commanderMultiTree: CommanderMultiTree): Subgraph = {
    val subgraph = planMultiTree(commanderMultiTree.commander, commanderMultiTree.multiTree)

    val in = Vertex[RunContext, Payload](CommanderHead(commanderMultiTree))
    val out = Vertex[RunContext, Payload](CommanderTail(commanderMultiTree))

    graph += Edge(in, subgraph.head)
    graph += Edge(subgraph.tail, out)

    Subgraph(in, out)
  }

  private[this] def planCommanderMultiTrees(commanderMultiTrees: Seq[CommanderMultiTree]): Subgraph = {
    val commanderSubgraphs = commanderMultiTrees.map(planCommanderMultiTree)

    // Always wired in parallel, no decorations.

    val in = Vertex[RunContext, Payload](Start)
    val out = Vertex[RunContext, Payload](Finish)

    commanderSubgraphs foreach { commanderSubgraph =>
      graph += Edge(in, commanderSubgraph.head)
      graph += Edge(commanderSubgraph.tail, out)
    }

    Subgraph(in, out)
  }

  // This is what actually creates and stores the RunnableGraph representing the input MultiTree.

  private[this] val plan = planCommanderMultiTrees(commanderMultiTrees)

  private[this] def checkForCycles(root: PayloadVertex[RunContext, Payload], commander: Commander): Unit =
    graph.findCycle(root) foreach { cycle =>
      // We found a cycle, so we need to try to communicate it to the user.  Turn the cycle into a list of segments,
      // each of which begins and ends with a CycleSegmentEnd, which we'll try to turn into English.
      //

      @tailrec
      def segmentize(todo: Iterable[Payload],
                     segments: List[List[Payload]] = Nil,
                     headDuplicated: Boolean = false): List[List[Payload]] =
        todo match {
          case Nil =>
            segments
          case (head: CycleSegmentEnd) :: Nil =>
            segments
          case (head: CycleSegmentEnd) :: tail =>
            // head is a CycleSegmentEnd, so we need to scan for the next CycleSegmentEnd to collect a segment.
            // This case also needs to determine if this is the first segment.  If so, it needs to duplicate the head
            // to the end of the to-do list. That's because the cycle that comes in has each member in it exactly once.
            // Since we want a list of segments that ends where it begins, it needs to begin and end with the same
            // payload.
            val segmentInnards = tail.takeWhile(! _.isInstanceOf[CycleSegmentEnd])
            val rest = tail.dropWhile(! _.isInstanceOf[CycleSegmentEnd])
            val segmentEnd = rest.head
            val newSegment = List(head) ++ segmentInnards ++ List(segmentEnd)
            val newTodo =
              if ( headDuplicated )
                rest
              else
                rest ++ List(head)
            segmentize(newTodo, segments ++ List(newSegment), true)
          case head :: tail =>
            // The cycle does not necessarily have a CycleSegmentEnd at both ends, so this case rotates the list
            // until it finds the first CycleSegmentEnd.
            segmentize(tail ++ List(head), segments, headDuplicated)
        }

      val segments = segmentize(cycle.map(_.payload))

      def scope(s: Scoped) = s.scope match {
        case GlobalScope => "global "
        case CommanderScope => ""
      }
      def leaf(l: MultiTreeLeaf) = l.name.map(n => s"""mandate "$n"""").getOrElse(s"mandate ${l.mandate}")
      def branch(b: MultiTreeBranch) = b.name.map(n => s"""branch "$n"""").getOrElse("unnamed branch")
      def barrier(b: Barrier) = b.name.map(n => s"""${scope(b)}barrier "$n"""").getOrElse("unnamed ${scope(b)}barrier")
      def resource(r: Resource) = s"${scope(r)}resource $r"

      def toEnglish(segment: List[Payload]): Iterable[String] = segment match {
        case Leaf(pre, _) :: Sequencer(seq, _) :: Leaf(post, _) :: Nil =>
          Iterable(s"  ${leaf(pre)} must precede ${leaf(post)} due to series ordering in ${branch(seq)}")
        case Leaf(pre, _) :: ResourcePayload(r) :: Leaf(post, _) :: Nil =>
          Iterable(s"  ${leaf(pre)} must precede ${leaf(post)} due to ${resource(r)}")
        case BarrierPayload(b) :: Leaf(post, _) :: Nil =>
          Iterable(s"  ${barrier(b)} must precede ${leaf(post)} due to AfterBarrier decoration")
        case Leaf(pre, _) :: BarrierPayload(b) :: Nil =>
          Iterable(s"  ${leaf(pre)} must precede ${barrier(b)} due to BeforeBarrier decoration")
        case _ =>
          val lines =
            segment map { p => p match {
              case Leaf(l, _) => s"    ${leaf(l)}"
              case BarrierPayload(b) => s"    ${barrier(b)}"
              case ResourcePayload(r) => s"    ${resource(r)}"
              case Sequencer(b, _) => s"    series sequencing node from ${branch(b)}"
              case _ => s"    $p"
            }}

          "  indescribable segment consisting of the following vertices:" :: lines
      }

      val lines = segments.flatMap(toEnglish).mkString("\n")

      throw new AbortException(s"""circular dependency detected for commander "$commander"\n$lines""")
    }

  // This prevents us from running validation more than once (it is immutable, after all).

  private[this] var isValidated = false

  // Validation should look for anything that should prevent the user from running this plan.  Right now, that only
  // includes looking for cycles.

  def validate(): Unit = if ( ! isValidated ) {
    graph.vertices collect {
      case pv: PayloadVertex[RunContext, Payload] => (pv, pv.payload)
    } foreach {
      case (pv, CommanderHead(CommanderMultiTree(commander, _))) => checkForCycles(pv, commander)
      case _ =>
    }

    // TODO: add check for same number of ins and outs for semaphores.

    isValidated = true
  }

  def toDot(pw: PrintWriter) = {

    def attrs(v: Vertex[RunContext, Payload]): Map[String, Any] =
      v match {
        case sv: SemaphoreVertex[RunContext, Payload] =>
          val name = sv.name.getOrElse("<unnamed>")
          val label = s"$name (${sv.permits})"
            Map("shape" -> "box", "style" -> "filled", "color" -> "red", "label" -> label)

        case pv: PayloadVertex[RunContext, Payload] => pv.payload match {
          case Start =>
            Map("shape" -> "doublecircle", "label" -> "START")
          case Finish =>
            Map("shape" -> "doublecircle", "label" -> "FINISH")
          case Leaf(l, c) =>
            Map("shape" -> "box", "style" -> "filled", "label" -> l.mandate.toString)
          case BranchHead(b) =>
            Map("shape" -> "cds", "style" -> "filled", "color" -> "green", "label" -> b.name.getOrElse("?"))
          case BranchTail(b) =>
            Map("shape" -> "cds", "style" -> "filled", "color" -> "green", "label" -> b.name.getOrElse("?"), "shape" -> "cds", "orientation" -> 180)
          case CommanderHead(c) =>
            Map("shape" -> "cds", "style" -> "filled", "color" -> "purple", "label" -> c.commander.toString, "shape" -> "cds")
          case CommanderTail(c) =>
            Map("shape" -> "cds", "style" -> "filled", "color" -> "purple", "label" -> c.commander.toString, "shape" -> "cds", "orientation" -> 180)
          case BarrierPayload(b) =>
            Map("style" -> "filled", "color" -> "yellow", "label" -> b.name.getOrElse("?"))
          case ResourcePayload(r) =>
            Map("style" -> "filled", "color" -> "yellow", "label" -> r.toString)
          case _: Sequencer =>
            Map("shape" -> "point")
        }
      }

    graph.toDot(pw, attrs _)
  }
}
