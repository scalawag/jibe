package org.scalawag.jibe.backend

import java.io.{File, PrintWriter}

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.mandate._
import org.scalawag.jibe.report.{ExecutiveStatus, MandateStatus}
import org.scalawag.jibe.report.JsonFormat._

private[backend]
trait MandateJob {
  val dir: File
  val mandate: Mandate
  val takeAction: Boolean

  protected[this] var status: FileBackedStatus[MandateStatus, _] =
    new FileBackedStatus(dir / "mandate.js", MandateStatus(mandate.toString, mandate.description, mandate.isInstanceOf[CompositeMandateBase], takeAction))

  def executiveStatus = status.get.executiveStatus
  def executiveStatus_=(es: ExecutiveStatus.Value) = {
    status.mutate(_.copy(executiveStatus = Some(es)))
    fireCompleted()
  }

  // Not thread-safe but I know that this is only going to be called by one mandate right now (the parent).

  private[this] var completedListeners = Seq.empty[(MandateJob, MandateStatus) => Unit]

  def addCompletedListener(listener: (MandateJob, MandateStatus) => Unit) = {
    completedListeners = completedListeners :+ listener
  }

  protected[this] def fireCompleted(): Unit = {
    completedListeners.foreach(_.apply(this, status.get))
  }
}

object MandateJob {
  def apply(dir: File, mandate: Mandate, commander: Commander, takeAction: Boolean) = mandate match {
    case m: StatelessMandate     => new StatelessMandateJob(dir, m, commander, takeAction)
    case m: StatefulMandate[_]   => new StatefulMandateJob(dir, m, commander, takeAction)
    case m: CompositeMandateBase => new CompositeMandateJob(dir, m, commander, takeAction)
  }

  def apply(dir: File, mandate: RunMandate, takeAction: Boolean) =
    new RunMandateJob(dir, mandate, takeAction)
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
    if ( status.get.startTime.isDefined )
      throw new IllegalStateException(s"MandateJob $this has already been started")

    status.mutate(_.copy(startTime = Some(System.currentTimeMillis)))

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

    status.mutate(_.copy(endTime = Some(System.currentTimeMillis), executiveStatus = Some(outcome)))
    fireCompleted()

    status.get
  }
}

private[backend]
case class StatelessMandateJob(override val dir: File,
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
case class StatefulMandateJob[A](override val dir: File,
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

private[backend]
abstract class ParentMandateJob(val children: Seq[MandateJob]) extends MandateJob {
  private[this] var childrenReported = 0

  children.foreach(_.addCompletedListener(this.childCompleted))

  private[this] def childCompleted(child: MandateJob, report: MandateStatus) = synchronized {
    childrenReported += 1
    val allChildrenReported = childrenReported == children.size

    status.mutate { r =>

      // Incorporate this child's report into our report.

      def incrementValue[A](map: Option[Map[A, Int]], key: Option[A]) =
        key match {
          case None =>
            map

          case Some(k) =>
            val m = map.getOrElse(Map.empty)
            Some(m + ( k -> ( m.getOrElse(k, 0) + 1 ) ))
        }

      val newChildStatusCounts =
        incrementValue(r.childStatusCounts, report.executiveStatus)

      // Possibly update our outcome, if we can.

      // See if we have enough information to assign an outcome yet.  Several of the child outcomes can determine the
      // parent's outcome (e.g., any child failure means the parent failed).  This is true for everything except
      // UNNEEDED in the order of precedence below.  If all we get back is UNNEEDED. we need to wait until every
      // child has reported back to make a determination.

      val newExecutiveStatus = {
        import ExecutiveStatus._
        if ( report.executiveStatus.contains(FAILURE) || status.get.executiveStatus.contains(FAILURE) )
          Some(FAILURE)
        else if ( report.executiveStatus.contains(BLOCKED) || status.get.executiveStatus.contains(BLOCKED) )
          Some(BLOCKED)
        else if ( report.executiveStatus.contains(SUCCESS) || status.get.executiveStatus.contains(SUCCESS) )
          Some(SUCCESS)
        else if ( allChildrenReported )
          Some(UNNEEDED)
        else
          None
      }

      val newEndTime =
        if ( allChildrenReported )
          Some(System.currentTimeMillis)
        else
          None

      // There are still outstanding children.  Set anything we already know about from the above logic.

      r.copy(
        endTime = newEndTime,
        executiveStatus = newExecutiveStatus,
        childStatusCounts = newChildStatusCounts
      )
    }

    if ( allChildrenReported )
      fireCompleted()
  }
}

private[backend]
class CompositeMandateJob(override val dir: File,
                          override val mandate: CompositeMandateBase,
                          val commander: Commander,
                          override val takeAction: Boolean)
  extends ParentMandateJob({
    val width = math.log10(mandate.mandates.length).toInt + 1
    mandate.mandates.zipWithIndex map { case (m, n) =>
      val subdir = s"%0${width}d".format(n + 1) + m.description.map(s => "_" + s.replaceAll("\\W+", "_")).getOrElse("")
      MandateJob(dir / subdir, m, commander, takeAction)
    }
  })

private[backend]
class RunMandateJob(override val dir: File,
                    override val mandate: RunMandate,
                    override val takeAction: Boolean)
  extends ParentMandateJob(
    mandate.mandates map { case m =>
      MandateJob(dir / m.commander.toString, m, m.commander, takeAction)
    }
  )
