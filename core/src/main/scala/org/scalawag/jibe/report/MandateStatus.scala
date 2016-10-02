package org.scalawag.jibe.report

case class MandateStatus(mandate: String,
                         description: Option[String],
                         composite: Boolean,
                         takeAction: Boolean,
                         startTime: Option[Long] = None,
                         endTime: Option[Long] = None,
                         executiveStatus: Option[ExecutiveStatus.Value] = None,
                         childStatusCounts: Option[Map[ExecutiveStatus.Value, Int]] = None)

object ExecutiveStatus extends Enumeration {
  val UNNEEDED = Value // action had already been taken
  val NEEDED = Value   // action is needed, but we're in read-only mode
  val FAILURE = Value  // something failed (either test or action)
  val BLOCKED = Value  // a prerequisite was not met due to failure (doesn't apply to read-only mode)
  val SUCCESS = Value  // action was required and taken (doesn't apply to read-only mode)
}

// maybe have a single outcome for the overall.  THat would leave room for something like "waiting" or "blocked" (i.e., by prereqs).