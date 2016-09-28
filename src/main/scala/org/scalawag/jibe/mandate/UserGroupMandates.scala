package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.{GroupResource, UserResource}

case class AddUserToGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"add user to groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    Some(runCommand(command.IsUserInAllGroups(user, groups)))

  override def takeActionIfNeeded(implicit context: MandateExecutionContext) = ifNeeded {
    runCommand(command.AddUserToGroups(user, groups))
  }
}

case class RemoveUserFromGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = Iterable(UserResource(user))

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    Some(!runCommand(command.IsUserInAnyGroups(user, groups)))

  override def takeActionIfNeeded(implicit context: MandateExecutionContext) = ifNeeded {
    runCommand(command.RemoveUserFromGroups(user, groups))
  }
}
