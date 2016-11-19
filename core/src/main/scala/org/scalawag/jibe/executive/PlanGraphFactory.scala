package org.scalawag.jibe.executive

import java.io.PrintWriter

import org.scalawag.jibe.backend.{Commander, MandateExecutionLogging, RunnableGraphFactory}
import org.scalawag.jibe.multitree.{MandateExecutionContext, _}
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report._

import scala.util.{Failure, Success, Try}

private[executive] object PlanGraphFactory extends RunnableGraphFactory {
  override type VisitContextType = VisitContext
  override type VertexType = PlanGraphVertex

  case class VisitContext(takeAction: Boolean,
                          multiTreeIdMaps: Map[Commander, MultiTreeIdMap],
                          reportsById: Map[(Commander, MultiTreeId), Report])


  // These are the things that we'll store in the RunnableGraph.

  sealed trait PlanGraphVertex extends Vertex

  // These are things that need to be reported on when a cycle is detected. Other things are immaterial.

  sealed trait CycleSegmentEnd

  // First, a set of payloads that don't do anything when they're encountered by the RunnableGraph.  They always succeed.

  sealed trait NoopSignalState
  case object Complete extends NoopSignalState

  sealed trait MultiTreeState {
    val reportStatus: Report.Status
  }
  case class NotExecuted(reportStatus: Report.Status) extends MultiTreeState  // no action was taken
  case class Executed(reportStatus: Report.Status) extends MultiTreeState     // an action was taken and succeeded
  case class Failed(reportStatus: Report.Status) extends MultiTreeState       // an action was taken and failed
  case class Aborted(reportStatus: Report.Status) extends MultiTreeState      // no action was taken due to an Abort signal (which will cascade)
  // no action was taken due to bypass signal (which will cascade)
  case class BypassedUntil(reportStatus: Report.Status, until: MultiTreeVertex) extends MultiTreeState

  sealed trait MultiTreeSignal
  case object Proceed extends MultiTreeSignal
  case object Abort extends MultiTreeSignal
  case class BypassUntil(until: MultiTreeVertex) extends MultiTreeSignal

  // This just means that we know what kind of state it can hold and what kind of signals it can receive.  This is
  // really important for created edges from Subgraphs, as we need to know the signal and state types of the head and
  // tail.  This way, they're all the same (and statically known).

  sealed trait MultiTreeVertex extends PlanGraphVertex {
    override type SignalType = MultiTreeSignal
    override type StateType = MultiTreeState

    override def visit(signals: List[Option[MultiTreeSignal]])(implicit visitContext: VisitContext) =
      if ( signals.exists(_ == Some(Abort)) ) {
        // There's at least one Abort signal, we know immediately that we're blocked.
        setState(visitContext, Aborted(BLOCKED))
      } else {
        val bypassUntil = signals.collectFirst {
          case s @ Some(BypassUntil(v)) => v
        }

        bypassUntil match {
          case Some(v) =>
            // There's at least one Bypass signal, we know immediately that we're being bypassed.
            setState(visitContext, BypassedUntil(SKIPPED, v))

          case None =>
            if ( signals.exists(_ == None) ) {
              // If any signal is outstanding, we can't really know what our state is yet.
              None
            } else {
              // Otherwise, it looks like all our signals are Proceed, so let's do that.
              Some(proceed(visitContext))
            }
        }
      }

    protected def setState(visitContext: VisitContext, state: MultiTreeState): Option[MultiTreeState] = Some(state)
    protected def proceed(visitContext: VisitContext): MultiTreeState = Executed(SUCCESS)
  }

  // Subgraphs just for convenience when building up a large graph out of smaller graphs.  Using the edge method,
  // we'll create edges between two subgraphs and the tail of the first to the head of the second will be connected.

  sealed trait Subgraph {
    type HeadType <: MultiTreeVertex
    type TailType <: MultiTreeVertex

    val head: HeadType
    val tail: TailType
  }

  case class LeafSubgraph(leaf: LeafVertex) extends Subgraph {
    override type HeadType = LeafVertex
    override type TailType = LeafVertex
    override val head = leaf
    override val tail = leaf
  }

  case class BranchSubgraph(h: SubgraphHead, t: SubgraphTail) extends Subgraph {
    override type HeadType = SubgraphHead
    override type TailType = SubgraphTail
    override val head = h
    override val tail = t
  }

  object Subgraph {
    def apply(leaf: LeafVertex) = LeafSubgraph(leaf)
    def apply(h: SubgraphHead, t: SubgraphTail) = BranchSubgraph(h, t)
  }

  trait SubgraphHead extends MultiTreeVertex
  trait SubgraphTail extends MultiTreeVertex

  trait NoopVertex extends PlanGraphVertex {
    final override type SignalType = NoopSignalState
    final override type StateType = NoopSignalState

    // Vertices of this type don't have any work that they need to do themselves, so they just wait around for all
    // of their signals to become available and then enter the Complete state.
    override def visit(signals: List[Option[NoopSignalState]])(implicit visitContext: VisitContext) =
      if ( signals.exists(_.isEmpty) )
        None
      else
        Some(Complete)
  }

  // Some of the commander arguments are needed here (even though they're never accessed to ensure uniqueness across
  // commanders for the corresponding items in the RunnableGraph.

  case class BranchHead(branch: MultiTreeBranch, commander: Commander, semaphores: Set[Semaphore] = Set.empty) extends SubgraphHead {
    override val semaphoresToAcquire = semaphores
  }
  case class BranchTail(branch: MultiTreeBranch, commander: Commander, semaphores: Set[Semaphore] = Set.empty) extends SubgraphTail {
    override val semaphoresToRelease = semaphores
  }
  case class CommanderHead(commanderMultiTree: CommanderMultiTree) extends SubgraphHead
  case class CommanderTail(commanderMultiTree: CommanderMultiTree) extends SubgraphTail

  case class ResourceVertex(resource: Resource, commander: Option[Commander]) extends NoopVertex
  case class BarrierVertex(barrier: Barrier, commander: Option[Commander]) extends NoopVertex with CycleSegmentEnd
  case object Start extends NoopVertex
  case object Finish extends NoopVertex

  case class Sequencer(val branch: MultiTreeBranch, val commander: Commander) extends NoopVertex with OnlyIdentityEquals

  sealed trait FlagSignal
  case object SetFlag extends FlagSignal
  case object ClearFlag extends FlagSignal
  case object Abstain extends FlagSignal

  sealed trait FlagState
  case object Flagged extends FlagState
  case object Unflagged extends FlagState

  case class FlagVertex(flag: Flag, commander: Option[Commander]) extends PlanGraphVertex {
    override type SignalType = FlagSignal
    override type StateType = FlagState

    override def visit(signals: List[Option[FlagSignal]])(implicit visitContext: VisitContext) =
      if ( flag.style == ConjunctionFlag && signals.exists(_ == Some(ClearFlag)) )
        // Any ClearFlag signal determines a ConjunctionFlag
        Some(Unflagged)
      else if ( flag.style == DisjunctionFlag && signals.exists(_ == Some(SetFlag)) )
        // Any SetFlag signal determines a DisjunctionFlag
        Some(Flagged)
      else if ( signals.exists(_.isEmpty) )
        // There are still outstanding signals, so we can't determine anything yet.
        None
      else
        Some(Unflagged)
  }

  // This one actually does stuff because it represents a MultiTreeLeaf (that contains a Mandate).

  case class LeafVertex(leaf: MultiTreeLeaf,
                        commander: Commander,
                        semaphores: Set[Semaphore] = Set.empty) extends MultiTreeVertex with CycleSegmentEnd
  {
    override val semaphoresToAcquire = semaphores
    override val semaphoresToRelease = semaphores

    private[this] def getReport(context: VisitContext) = {
      val id = context.multiTreeIdMaps(commander).getId(leaf)
      context.reportsById(commander, id)
    }

    // TODO: maybe combine this method with proceed since they do similar things and require similar parameters.
    override protected def setState(visitContext: VisitContext, state: MultiTreeState) =
      state match {
        case Aborted(_) =>
          getReport(visitContext).status.mutate(_.copy(status = BLOCKED, leafStatusCounts = Map(BLOCKED -> 1)))
          Some(state)

        case BypassedUntil(_, v) =>
          getReport(visitContext).status.mutate(_.copy(status = SKIPPED, leafStatusCounts = Map(SKIPPED -> 1)))
          Some(state)

        case s => Some(s) // I happen to know that nothing calls this method with another state.
      }

    override protected[this] def proceed(visitContext: VisitContext) = leaf.mandate match {
      case sm: StatelessMandate => StatelessMandateJob(sm).go(visitContext)
      case sm: StatefulMandate[_] => StatefulMandateJob(sm).go(visitContext)
    }

    private[this] trait MandateJob {
      protected[this] val mandate: Mandate
      protected[this] def isActionCompleted(implicit context: MandateExecutionContext): Option[Boolean]
      protected[this] def takeActionIfNeeded(implicit context: MandateExecutionContext): Status

      protected[this] def logCall[A](label: String)(fn: => A)(implicit context: MandateExecutionContext): A = {
        context.log.info(MandateExecutionLogging.FunctionStart)(label)
        val answer = fn
        context.log.info(MandateExecutionLogging.FunctionReturn)(answer.toString)
        answer
      }

      protected[this] def takeActionIfNeededLogic(isActionCompletedFn: => Option[Boolean], takeActionFn: => Unit) =
        isActionCompletedFn match {
          case Some(true) => UNNEEDED
          case _ =>
            takeActionFn
            SUCCESS
        }

      protected[this] def callIsActionCompletedImpl(fn: => Boolean)(implicit context: MandateExecutionContext): Option[Boolean] =
        logCall("isActionCompleted") {
          try {
            Some(fn)
          } catch {
            case ex: UnsupportedOperationException => None
          }
        }

      protected[this] def callTakeActionImpl(fn: => Unit)(implicit context: MandateExecutionContext) =
        logCall("takeAction") {
          fn
        }

      def go(visitContext: VisitContext): MultiTreeState = {
        val report = getReport(visitContext)

        if ( report.status.get.status == PENDING ) {
          report.status.mutate(_.copy(startTime = Some(System.currentTimeMillis), status = RUNNING, leafStatusCounts = Map(RUNNING -> 1)))

          val log = MandateExecutionLogging.createMandateLogger(report.dir)
          val mandateExecutionContext = MandateExecutionContext(commander, log)

          try {
            val state =
              if (visitContext.takeAction)
                Executed(takeActionIfNeeded(mandateExecutionContext))
              else
                isActionCompleted(mandateExecutionContext) match {
                  case Some(true) => NotExecuted(UNNEEDED)
                  case _ => NotExecuted(NEEDED)
                }
            report.status.mutate(_.copy(endTime = Some(System.currentTimeMillis),
                                        status = state.reportStatus,
                                        leafStatusCounts = Map(state.reportStatus -> 1)))
            state
          } catch {
            case ex: Exception =>
              log.error(MandateExecutionLogging.ExceptionStackTrace) { pw: PrintWriter =>
                ex.printStackTrace(pw)
              }
              report.status.mutate(_.copy(endTime = Some(System.currentTimeMillis), status = FAILURE, leafStatusCounts = Map(FAILURE -> 1)))
              Failed(FAILURE)
          }

        } else {
          throw new IllegalStateException(s"MandateJob $this on $commander is in an invalid state: ${report.status.get.status}")
        }
      }
    }

    private[this] case class StatelessMandateJob(override val mandate: StatelessMandate) extends MandateJob {
      override protected[this] def isActionCompleted(implicit context: MandateExecutionContext) =
        callIsActionCompletedImpl(mandate.isActionCompleted)

      override protected[this] def takeActionIfNeeded(implicit context: MandateExecutionContext) =
        takeActionIfNeededLogic(
          callIsActionCompletedImpl(mandate.isActionCompleted),
          callTakeActionImpl(mandate.takeAction)
        )
    }

    private[this] case class StatefulMandateJob[A](override val mandate: StatefulMandate[A]) extends MandateJob {
      override protected[this] def isActionCompleted(implicit context: MandateExecutionContext): Option[Boolean] = {
        val state = logCall("createState")(mandate.createState)
        try {
          callIsActionCompletedImpl(mandate.isActionCompleted(state))
        } finally {
          logCall("cleanupState")(mandate.cleanupState(state))
        }
      }

      override protected[this] def takeActionIfNeeded(implicit context: MandateExecutionContext) = {
        val state = logCall("createState")(mandate.createState)
        try {
          takeActionIfNeededLogic(
            callIsActionCompletedImpl(mandate.isActionCompleted(state)),
            callTakeActionImpl(mandate.takeAction(state))
          )
        } finally {
          logCall("cleanupState")(mandate.cleanupState(state))
        }
      }
    }
  }

  // Supported edge types, based on state-to-signal propagation.

  implicit def noopStateToMultiTreeSignal(state: Try[NoopSignalState]): MultiTreeSignal = Proceed

  implicit def anyToNoopSignal(any: Try[Any]): NoopSignalState = Complete

  implicit def reportStatusToMultiTreeSignal(tryStatus: Try[MultiTreeState]): MultiTreeSignal = tryStatus match {
    case Success(state) =>
      state match {
        case NotExecuted(_) => Proceed
        case Executed(_) => Proceed
        case Failed(_) => Abort
        case Aborted(_) => Abort
        case BypassedUntil(_, v) => BypassUntil(v)
      }
    case Failure(_) => Abort
  }
}
