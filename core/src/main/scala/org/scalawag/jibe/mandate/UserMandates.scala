package org.scalawag.jibe.mandate

import org.scalawag.jibe.mandate.command.User
import org.scalawag.jibe.multitree._

case class CreateOrUpdateUser(user: User) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"update user: ${user.name}"

  override val decorations = Set[MultiTreeDecoration](
    Prerequisites(
      user.primaryGroup.map(GroupResource),
      user.home.map(FileResource),
      user.shell.map(FileResource)
    ),
    Consequences(
      UserResource(user.name)
    )
  )

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    runCommand(command.DoesUserExist(user))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.CreateOrUpdateUser(user))
}

case class DeleteUser(userName: String) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"delete user: ${userName}"

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    ! runCommand(command.DoesUserExist(User(userName)))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.DeleteUser(userName))
}
