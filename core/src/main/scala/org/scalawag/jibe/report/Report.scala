package org.scalawag.jibe.report

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
}
