package org.scalawag.jibe.mandate

import org.scalawag.jibe.multitree._

case class AddUserToGroups(user: String, groups: String*) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"add user to groups: $user -> ${groups.mkString(" ")}"

  override val decorations = Set[MultiTreeDecoration](
    Prerequisites(UserResource(user)),
    Prerequisites(groups.map(GroupResource))
  )

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    runCommand(command.IsUserInAllGroups(user, groups))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.AddUserToGroups(user, groups))
}

case class RemoveUserFromGroups(user: String, groups: String*) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"remove user from groups: $user -> ${groups.mkString(" ")}"

  override val decorations = Set[MultiTreeDecoration](
    Prerequisites(UserResource(user))
    //      ,Prerequisites(groups.map(GroupResource)) // TODO: should this require that the groups exist?
  )

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    ! runCommand(command.IsUserInAnyGroups(user, groups))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.RemoveUserFromGroups(user, groups))
}
