package org.scalawag.jibe.mandate

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.{Commander, GroupResource, UserResource}

case class AddUserToGroups(user: String, groups: String*) extends CheckableMandate {
  override val description = Some(s"add user to groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean =
    commander.execute(resultsDir / "isActionCompleted", command.IsUserInAllGroups(user, groups))

  override def takeAction(commander: Commander, resultsDir: File): Unit =
    commander.execute(resultsDir / "takeAction", command.AddUserToGroups(user, groups))
}

case class RemoveUserFromGroups(user: String, groups: String*) extends CheckableMandate {
  override val description = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = Iterable(UserResource(user))

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean =
    !commander.execute(resultsDir / "isActionCompleted", command.IsUserInAnyGroups(user, groups))

  override def takeAction(commander: Commander, resultsDir: File): Unit =
    commander.execute(resultsDir / "takeAction", command.RemoveUserFromGroups(user, groups))
}
