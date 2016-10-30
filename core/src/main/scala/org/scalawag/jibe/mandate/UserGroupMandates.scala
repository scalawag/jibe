package org.scalawag.jibe.mandate

import org.scalawag.jibe.multitree._

object AddUserToGroups {
  case class AddUserToGroups(user: String, groups: String*) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      runCommand(command.IsUserInAllGroups(user, groups))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.AddUserToGroups(user, groups))
  }

  def apply(user: String, groups: String*) = MultiTreeLeaf(
    mandate = new AddUserToGroups(user, groups:_*),
    name = Some(s"add user to groups: $user -> ${groups.mkString(" ")}"),
    decorations = Set(
      Prerequisites(UserResource(user)),
      Prerequisites(groups.map(GroupResource))
    )
  )
}

object RemoveUserFromGroups {
  case class RemoveUserFromGroups(user: String, groups: String*) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      ! runCommand(command.IsUserInAnyGroups(user, groups))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.RemoveUserFromGroups(user, groups))
  }

  def apply(user: String, groups: String*) = MultiTreeLeaf(
    mandate = new RemoveUserFromGroups(user, groups:_*),
    name = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}"),
    decorations = Set(
      Prerequisites(UserResource(user))
//      ,Prerequisites(groups.map(GroupResource)) // TODO: should this require that the groups exist?
    )
  )
}
