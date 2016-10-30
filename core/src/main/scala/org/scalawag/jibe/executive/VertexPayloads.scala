package org.scalawag.jibe.executive

import java.io.PrintWriter

import org.scalawag.jibe.backend.{Commander, MandateExecutionLogging, RunnableGraph}
import org.scalawag.jibe.mandate.MandateExecutionContext
import org.scalawag.jibe.multitree._
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report._

case class RunContext(takeAction: Boolean,
                      multiTreeIdMaps: Map[Commander, MultiTreeIdMap],
                      reportsById: Map[(Commander, MultiTreeId), Report])

// These are the things that we'll store in the ExecutionGraph.

private[executive] sealed trait Payload extends RunnableGraph.Payload[RunContext]

// These are things that need to be reported on when a cycle is detected. Other things are immaterial.

private[executive] sealed trait CycleSegmentEnd extends Payload

// First, a set of payloads that don't do anything when they're encountered by the RunnableGraph.  They always succeed.

private[executive] sealed trait NoopPayload extends Payload {
  override def run(runContext: RunContext) = true
  override def abort(runContext: RunContext) = {}
}

private[executive] case class BranchHead(branch: MultiTreeBranch) extends NoopPayload
private[executive] case class BranchTail(branch: MultiTreeBranch) extends NoopPayload
private[executive] case class CommanderHead(commanderMultiTree: CommanderMultiTree) extends NoopPayload
private[executive] case class CommanderTail(commanderMultiTree: CommanderMultiTree) extends NoopPayload
private[executive] case class ResourcePayload(resource: Resource) extends NoopPayload
private[executive] case class BarrierPayload(barrier: Barrier) extends NoopPayload with CycleSegmentEnd
private[executive] case object Start extends NoopPayload
private[executive] case object Finish extends NoopPayload

private[executive] case class Sequencer(val branch: MultiTreeBranch, val commander: Commander) extends NoopPayload {
  // There are going to be potentially many of these with the same arguments.  They each need to be distinct.
  override def equals(any: Any) = any match {
    case that: AnyRef => this eq that
    case _ => false
  }
}

// This one actually does stuff because it represents a MultiTreeLeaf (that contains a Mandate).

private[executive] case class Leaf(leaf: MultiTreeLeaf, commander: Commander) extends Payload with CycleSegmentEnd {
  private[this] def getReport(context: RunContext) = {
    val id = context.multiTreeIdMaps(commander).getId(leaf)
    context.reportsById(commander, id)
  }

  override def run(runContext: RunContext) = leaf.mandate match {
    case sm: StatelessMandate => StatelessMandateJob(sm).go(runContext)
    case sm: StatefulMandate[_] => StatefulMandateJob(sm).go(runContext)
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

    def go(runContext: RunContext): Boolean = {
      val report = getReport(runContext)

      if ( report.status.get.status == PENDING ) {
        report.status.mutate(_.copy(startTime = Some(System.currentTimeMillis), status = RUNNING, leafStatusCounts = Map(RUNNING -> 1)))

        val log = MandateExecutionLogging.createMandateLogger(report.dir)
        val mandateExecutionContext = MandateExecutionContext(commander, log)

        try {
          val outcome =
            if (runContext.takeAction)
              takeActionIfNeeded(mandateExecutionContext)
            else
              isActionCompleted(mandateExecutionContext) match {
                case Some(true) => UNNEEDED
                case _ => NEEDED
              }
          report.status.mutate(_.copy(endTime = Some(System.currentTimeMillis), status = outcome, leafStatusCounts = Map(outcome -> 1)))
          true
        } catch {
          case ex: Exception =>
            log.error(MandateExecutionLogging.ExceptionStackTrace) { pw: PrintWriter =>
              ex.printStackTrace(pw)
            }
            report.status.mutate(_.copy(endTime = Some(System.currentTimeMillis), status = FAILURE, leafStatusCounts = Map(FAILURE -> 1)))
            false
        }

      } else if ( report.status.get.status != BLOCKED ) {
        throw new IllegalStateException(s"MandateJob $this on $commander is in an invalid state: ${report.status.get.status}")
      } else {
        true
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

  override def abort(runContext: RunContext) = {
    val report = getReport(runContext)
    report.status.mutate(_.copy(status = BLOCKED, leafStatusCounts = Map(BLOCKED -> 1)))
  }
}
