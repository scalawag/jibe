package org.scalawag.jibe.mandate

import org.scalawag.jibe.mandate.command.User
import org.scalawag.jibe.multitree._

object CreateOrUpdateUser {
  case class CreateOrUpdateUser(user: User) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      runCommand(command.DoesUserExist(user))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.CreateOrUpdateUser(user))
  }

  def apply(user: User) = new MultiTreeLeaf(
    mandate = new CreateOrUpdateUser(user),
    name = Some(s"update user: ${user.name}"),
    decorations = Set(
      Prerequisites(
        user.primaryGroup.map(GroupResource),
        user.home.map(FileResource),
        user.shell.map(FileResource)
      ),
      Consequences(
        UserResource(user.name)
      )
    )
  )
}

object DeleteUser {
  case class DeleteUser(userName: String) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      ! runCommand(command.DoesUserExist(User(userName)))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.DeleteUser(userName))
  }

  def apply(userName: String) = new MultiTreeLeaf(
    mandate = new DeleteUser(userName),
    name = Some(s"delete user: ${userName}")
  )
}
