package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend._

case class AddUserToGroups(user: String, groups: String*) extends StatelessMandate with MandateHelpers {
  override val description = Some(s"add user to groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    runCommand(command.IsUserInAllGroups(user, groups))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.AddUserToGroups(user, groups))
}

case class RemoveUserFromGroups(user: String, groups: String*) extends StatelessMandate with MandateHelpers {
  override val description = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = Iterable(UserResource(user))

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    ! runCommand(command.IsUserInAnyGroups(user, groups))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.RemoveUserFromGroups(user, groups))
}
