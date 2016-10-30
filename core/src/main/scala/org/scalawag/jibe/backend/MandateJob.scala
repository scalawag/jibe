package org.scalawag.jibe.backend

import java.io.{File, PrintWriter}
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.mandate._
import org.scalawag.jibe.multitree.MandateExecutionContext
import org.scalawag.jibe.report.{ExecutiveStatus, MandateStatus}
import org.scalawag.jibe.report.JsonFormat._

private[backend]
trait MandateJob {
  val id: String
  val dir: File
  val mandate: Mandate
  val takeAction: Boolean

  private[this] def fingerprint(m: Mandate): String = m match {
    case x: CompositeMandateBase => DigestUtils.md5Hex(x.mandates.map(fingerprint).mkString).toLowerCase
    case x => DigestUtils.md5Hex(x.toString).toLowerCase
  }

  protected[this] val _status: FileBackedStatus[MandateStatus, _] =
    new FileBackedStatus(dir / "mandate.js",
      MandateStatus(
        id,
        mandate.toString,
        fingerprint(mandate),
        mandate.description,
        mandate.isInstanceOf[CompositeMandateBase]
      )
    )

  def status = _status.get
  def executiveStatus = _status.get.executiveStatus
  def executiveStatus_=(es: ExecutiveStatus.Value) = {
    _status.mutate(_.copy(executiveStatus = es))
  }

  // This basically maintains its own list of listeners and filters out messages that don't include executiveStatus
  // changes.  It also adds the context of the mandate job that fired the event.

  private type StatusChangeListener = (MandateJob, MandateStatus, MandateStatus) => Unit

  private[this] var statusChangeListeners = new AtomicReference[Seq[StatusChangeListener]](Seq.empty)

  def addChangeListener(listener: StatusChangeListener) =
    statusChangeListeners.getAndUpdate(new UnaryOperator[Seq[StatusChangeListener]] {
      override def apply(t: Seq[StatusChangeListener]) = t :+ listener
    })

  private[this] def fireStatusChange(oldStatus: MandateStatus, newStatus: MandateStatus): Unit =
    statusChangeListeners.get.foreach(_.apply(this, oldStatus, newStatus))

  // register for lower-level change events
  _status.addChangeListener(fireStatusChange _)
}

object MandateJob {
  def apply(id: String, dir: File, mandate: Mandate, commander: Commander, takeAction: Boolean) = mandate match {
    case m: StatelessMandate     => new StatelessMandateJob(id, dir, m, commander, takeAction)
    case m: StatefulMandate[_]   => new StatefulMandateJob(id, dir, m, commander, takeAction)
    case m: CompositeMandateBase => new CompositeMandateJob(id, dir, m, commander, takeAction)
  }

  def apply(dir: File, mandate: RunMandate, takeAction: Boolean) = new RunMandateJob(dir, mandate, takeAction)
}

private[backend]
trait LeafMandateJob extends MandateJob {
  protected[this] val commander: Commander
  protected[this] def isActionCompleted(implicit context: MandateExecutionContext): Option[Boolean]
  protected[this] def takeActionIfNeeded(implicit context: MandateExecutionContext): ExecutiveStatus.Value

  private[backend] val log = MandateExecutionLogging.createMandateLogger(dir)

  protected[this] def logCall[A](label: String)(fn: => A)(implicit context: MandateExecutionContext): A = {
    context.log.info(MandateExecutionLogging.FunctionStart)(label)
    val answer = fn
    context.log.info(MandateExecutionLogging.FunctionReturn)(answer.toString)
    answer
  }

  protected[this] def takeActionIfNeededLogic(isActionCompletedFn: => Option[Boolean], takeActionFn: => Unit) =
    isActionCompletedFn match {
      case Some(true) => ExecutiveStatus.UNNEEDED
      case _ =>
        takeActionFn
        ExecutiveStatus.SUCCESS
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

  def go() = {
    if ( _status.get.executiveStatus != ExecutiveStatus.PENDING )
      throw new IllegalStateException(s"MandateJob $this has already been started")

    _status.mutate(_.copy(startTime = Some(System.currentTimeMillis), executiveStatus = ExecutiveStatus.RUNNING))

    val mec = MandateExecutionContext(commander, log)

    val outcome: ExecutiveStatus.Value =
      try {
        if (takeAction)
          takeActionIfNeeded(mec)
        else
          isActionCompleted(mec) match {
            case Some(true) => ExecutiveStatus.UNNEEDED
            case _ => ExecutiveStatus.NEEDED
          }
      } catch {
        case ex: Exception =>
          mec.log.error(MandateExecutionLogging.ExceptionStackTrace) { pw: PrintWriter =>
            ex.printStackTrace(pw)
          }
          ExecutiveStatus.FAILURE
      }

    _status.mutate(_.copy(endTime = Some(System.currentTimeMillis), executiveStatus = outcome))

    _status.get
  }
}

private[backend]
case class StatelessMandateJob(override val id: String,
                               override val dir: File,
                               override val mandate: StatelessMandate,
                               override val commander: Commander,
                               override val takeAction: Boolean)
  extends LeafMandateJob
{
  override protected[this] def isActionCompleted(implicit context: MandateExecutionContext) =
    callIsActionCompletedImpl(mandate.isActionCompleted)

  override protected[this] def takeActionIfNeeded(implicit context: MandateExecutionContext) =
    takeActionIfNeededLogic(
      callIsActionCompletedImpl(mandate.isActionCompleted),
      callTakeActionImpl(mandate.takeAction)
    )
}

private[backend]
case class StatefulMandateJob[A](override val id: String,
                                 override val dir: File,
                                 override val mandate: StatefulMandate[A],
                                 override val commander: Commander,
                                 override val takeAction: Boolean)
  extends LeafMandateJob
{
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

object ParentMandateJob {
  def zipWithIndexAndDirName[A <: Mandate](ms: Iterable[A]): Iterable[(A, Int, String)] = {
    val width = math.log10(ms.size).toInt + 1
    ms.zipWithIndex map { case (m, n) =>
      val subdir = s"%0${width}d".format(n) + m.description.map(s => "_" + s.replaceAll("\\W+", "_")).getOrElse("")
      (m, n, subdir)
    }
  }

  def getChildLeafStatusCounts(status: MandateStatus) =
    if ( status.composite ) {
      status.leafStatusCounts.get
    } else {
      Map(status.executiveStatus -> 1)
    }

  def addMap[K](l: Map[K, Int], r: Map[K, Int]): Map[K, Int] =
    ( l.keySet ++ r.keySet ) flatMap { k =>
      val sum = l.getOrElse(k, 0) + r.getOrElse(k, 0)
      if ( sum == 0 )
        None
      else
        Some(k -> sum)
    } toMap

  def subtractMap[K](l: Map[K, Int], r: Map[K, Int]): Map[K, Int] = addMap(l, r.mapValues(-_))
}

import ParentMandateJob._

private[backend]
abstract class ParentMandateJob(override val id: String, val children: Seq[MandateJob]) extends MandateJob {
  // initialize the leaf job state counts to all PENDING

  _status.mutate(_.copy(leafStatusCounts = Some(
    children.map(_.status).map(getChildLeafStatusCounts).reduce(ParentMandateJob.addMap[ExecutiveStatus.Value])
  )))

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

  private[this] def childStatusChanged(child: MandateJob,
                                       oldStatus: MandateStatus,
                                       newStatus: MandateStatus) = synchronized {

    // Incorporate this child's status into our status.
    _status.mutate { r =>

      // Update leaf status counts...
      val oldChildLeafStatusCounts = getChildLeafStatusCounts(oldStatus)
      val newChildLeafStatusCounts = getChildLeafStatusCounts(newStatus)

      val newLeafStatusCounts =
        addMap(subtractMap(r.leafStatusCounts.get, oldChildLeafStatusCounts), newChildLeafStatusCounts)

      // Assign a parent status based on the childrens' statuses. The precendence of child statuses wrt parent
      // status is defined below.  This means that if a parent's childrens' statuses include two different items in
      // the precedence list, the one earlier in the list is the status of the parent.  That means that every child
      // has to have the last status in the list for the parent to have that status and only one child must have the
      // first status for the parent to have that status.
      //
      // The only two which don't really need to be ordered are SUCCESS and NEEDED which are mutally exclusive,
      // depending on the mode we're in (isActionNeeded or takeAction). They're listed in arbitrary order.

      import ExecutiveStatus._

      val precedence = Seq(FAILURE, BLOCKED, RUNNING, PENDING, SUCCESS, NEEDED, UNNEEDED)

      val newExecutiveStatus = precedence.find( s => newLeafStatusCounts.get(s).exists(_ > 0) ).get

      // Update our earliest child start and latest child end times so that we'll have them when we need them.
      this.earliestChildStartTime = earliest(this.earliestChildStartTime, newStatus.startTime)
      this.latestChildEndTime = latest(this.latestChildEndTime, newStatus.endTime)

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
        executiveStatus = newExecutiveStatus,
        leafStatusCounts = Some(newLeafStatusCounts)
      )
    }
  }
}

private[backend]
class CompositeMandateJob(override val id: String,
                          override val dir: File,
                          override val mandate: CompositeMandateBase,
                          val commander: Commander,
                          override val takeAction: Boolean)
  extends ParentMandateJob(id,
    zipWithIndexAndDirName(mandate.mandates).toSeq map { case (m, n, d) =>
      MandateJob(s"${id}_${n}", dir / d, m, commander, takeAction)
    }
  )

private[backend]
class RunMandateJob(override val dir: File,
                    override val mandate: RunMandate,
                    override val takeAction: Boolean)
  extends ParentMandateJob("m0",
    zipWithIndexAndDirName(mandate.mandates).toSeq map { case (m, n, d) =>
      MandateJob(s"m0_${n}", dir / d, m, m.commander, takeAction)
    }
  )
