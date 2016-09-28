package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.{GroupResource, UserResource}

case class AddUserToGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"add user to groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean =
    runCommand("isActionCompleted", command.IsUserInAllGroups(user, groups))

  override def takeAction(implicit context: MandateExecutionContext): Unit =
    runCommand("takeAction", command.AddUserToGroups(user, groups))
}

case class RemoveUserFromGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = Iterable(UserResource(user))

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean =
    !runCommand("isActionCompleted", command.IsUserInAnyGroups(user, groups))

  override def takeAction(implicit context: MandateExecutionContext): Unit =
    runCommand("takeAction", command.RemoveUserFromGroups(user, groups))
}
