package org.scalawag.jibe.report

case class Run(version: Int,
               timestamp: Long,
               id: String,
               takeAction: Boolean,
               mandate: Option[MandateStatus] = None)

case class MandateStatus(id: String,
                         mandate: String,
                         fingerprint: String,
                         description: Option[String],
                         composite: Boolean,
                         startTime: Option[Long] = None,
                         endTime: Option[Long] = None,
                         executiveStatus: ExecutiveStatus.Value = ExecutiveStatus.PENDING,
                         leafStatusCounts: Option[Map[ExecutiveStatus.Value, Int]] = None)

object ExecutiveStatus extends Enumeration {
  val PENDING  = Value // action has not yet started
  val RUNNING  = Value // action has started but has not completed
  val UNNEEDED = Value // action had already been taken, so none was taken
  val NEEDED   = Value // action is needed, but no actions are being taken this run
  val FAILURE  = Value // something failed (either test or action)
  val BLOCKED  = Value // a prerequisite was not met due to failure
  val SUCCESS  = Value // action was required, taken and completed successfully
}
