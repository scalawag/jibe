package org.scalawag.jibe.report

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.FileBackedStatus
import org.scalawag.jibe.multitree.MultiTreeId
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report.JsonFormats._

object Report {
  sealed trait Status
  case object PENDING  extends Status // action has not yet started
  case object RUNNING  extends Status // action has started but has not completed
  case object UNNEEDED extends Status // action had already been taken, so none was taken
  case object NEEDED   extends Status // action is needed, but no actions are being taken this run
  case object FAILURE  extends Status // action failed (either test or action)
  case object BLOCKED  extends Status // action was blocked by failure of a prerequisite
  case object SKIPPED  extends Status // action required activation but was not activated
  case object SUCCESS  extends Status // action was required, taken and completed successfully

  object Status {
    val values = Set(PENDING, RUNNING, UNNEEDED, NEEDED, FAILURE, BLOCKED, SKIPPED, SUCCESS)

    def withName(name: String): Status = values.find(_.toString == name).getOrElse {
      throw new IllegalArgumentException(s"unknown status: $name")
    }
  }

  /* Each report has an "attributes" object and a "status" object. The attributes are immutable and are determined by
   * the object that the report regards.  The status is always a ReportStatus, is mutable and changes as the object or its
   * children are run, reflecting the progress and outcome.
   */

  case class RunReportAttributes(version: Int,
                                 timestamp: Long,
                                 id: String,
                                 takeAction: Boolean)

  case class CommanderReportAttributes(description: String,
                                       root: MultiTreeId)

  case class BranchReportAttributes(id: MultiTreeId,
                                    name: Option[String],
                                    children: List[MultiTreeId])

  case class LeafReportAttributes(id: MultiTreeId,
                                  name: Option[String],
                                  stringRepresentation: String)

  case class ReportStatus(leafStatusCounts: Map[Report.Status, Int] = Map.empty,
                          status: Report.Status = Report.PENDING,
                          startTime: Option[Long] = None,
                          endTime: Option[Long] = None)
}

// Each Report manages a single report directory.

abstract class Report(val dir: File, initialStatus: ReportStatus) {

  val status = new FileBackedStatus(dir / "status.js", initialStatus)(ReportStatusFormat)

  protected type StatusChangeListener = (this.type, ReportStatus, ReportStatus) => Unit

  private[this] var statusChangeListeners = new AtomicReference[Seq[StatusChangeListener]](Seq.empty)

  def addChangeListener(listener: StatusChangeListener) =
    statusChangeListeners.getAndUpdate(new UnaryOperator[Seq[StatusChangeListener]] {
      override def apply(t: Seq[StatusChangeListener]) = t :+ listener
    })

  // register for lower-level change events so that we can propagate them out at this level.
  status.addChangeListener { (oldStatus: ReportStatus, newStatus: ReportStatus) =>
    statusChangeListeners.get.foreach(_.apply(this, oldStatus, newStatus))
  }
}

// Used to track reports for MultiTreeLeaf objects.

class LeafReport(override val dir: File)
  extends Report(dir, ReportStatus(Map(PENDING -> 1)))

object RollUpReport {
  private def addMap[K](l: Map[K, Int], r: Map[K, Int]): Map[K, Int] =
    ( l.keySet ++ r.keySet ) flatMap { k =>
      val sum = l.getOrElse(k, 0) + r.getOrElse(k, 0)
      if ( sum == 0 )
        None
      else
        Some(k -> sum)
    } toMap

  private def subtractMap[K](l: Map[K, Int], r: Map[K, Int]): Map[K, Int] = addMap(l, r.mapValues(-_))
}

import RollUpReport._

// Used to track anything that contains a roll-up report of multiple MultiTreeLeaves, including MultiTreeBranches
// and commander/run level reports.

class RollUpReport(override val dir: File, children: Iterable[Report])
  extends Report(dir, ReportStatus(children.map(_.status.get.leafStatusCounts).reduce(addMap(_,_))))
{

  // Keep track of the earliest start time and latest end time in case the events arrive from our children out of order.

  private[this] var earliestChildStartTime: Option[Long] = None
  private[this] var latestChildEndTime: Option[Long] = None

  private[this] def earliest(times: Option[Long]*) = times.flatten match {
    case seq if seq.isEmpty => None
    case seq => Some(seq.min)
  }

  private[this] def latest(times: Option[Long]*) = times.flatten match {
    case seq if seq.isEmpty => None
    case seq => Some(seq.max)
  }

  // Listen to all children for change events so that we can update our status when they change.

  children.foreach(_.addChangeListener(this.childStatusChanged))

  private[this] def childStatusChanged(child: Report,
                                       oldChildStatus: ReportStatus,
                                       newChildStatus: ReportStatus) = synchronized {

    // Incorporate this child's status into our status.
    status.mutate { r =>

      // Update leaf status counts...
      val oldChildLeafStatusCounts = oldChildStatus.leafStatusCounts
      val newChildLeafStatusCounts = newChildStatus.leafStatusCounts

      val newLeafStatusCounts =
        addMap(subtractMap(r.leafStatusCounts, oldChildLeafStatusCounts), newChildLeafStatusCounts)

      // Assign a parent status based on the childrens' statuses. The precendence of child statuses wrt parent
      // status is defined below.  This means that if a parent's childrens' statuses include two different items in
      // the precedence list, the one earlier in the list is the status of the parent.  That means that every child
      // has to have the last status in the list for the parent to have that status and only one child must have the
      // first status for the parent to have that status.
      //
      // The only two which don't really need to be ordered are SUCCESS and NEEDED which are mutally exclusive,
      // depending on the mode we're in (isActionNeeded or takeAction). They're listed in arbitrary order.

      val precedence = Seq(FAILURE, BLOCKED, RUNNING, PENDING, SUCCESS, NEEDED, UNNEEDED)

      val newStatus = precedence.find( s => newLeafStatusCounts.get(s).exists(_ > 0) ).get

      // Update our earliest child start and latest child end times so that we'll have them when we need them.
      this.earliestChildStartTime = earliest(this.earliestChildStartTime, newChildStatus.startTime)
      this.latestChildEndTime = latest(this.latestChildEndTime, newChildStatus.endTime)

      // Set the parent start time to the child start time if this event makes it so that no children are pending.
      // TODO: can we count on always receiving the earliest start time first?
      val newStartTime =
      if ( newLeafStatusCounts.getOrElse(PENDING, 0) < children.size )
        this.earliestChildStartTime
      else
        None

      // Set the parent end time to the child end time if all children are now done running.
      // TODO: can we count on always receiving the latest start time last?

      val newEndTime =
        if ( ( newLeafStatusCounts.getOrElse(RUNNING, 0) + newLeafStatusCounts.getOrElse(PENDING, 0) ) == 0 )
          this.latestChildEndTime
        else
          None

      // Update our status with everything we now know from the above logic.

      r.copy(
        startTime = newStartTime,
        endTime = newEndTime,
        status = newStatus,
        leafStatusCounts = newLeafStatusCounts
      )
    }
  }
}
