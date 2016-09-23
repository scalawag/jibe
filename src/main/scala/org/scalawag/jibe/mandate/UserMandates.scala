package org.scalawag.jibe.mandate

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.{Commander, FileResource, GroupResource, UserResource}

case class User(name: String,
                primaryGroup: Option[String] = None,
                uid: Option[Int] = None,
                home: Option[String] = None,
                shell: Option[String] = None,
                comment: Option[String] = None,
                system: Boolean = false)

object User {
  implicit def fromString(name: String) = User(name)
}

case class CreateOrUpdateUser(user: User) extends CheckableMandate {
  override val description = Some(s"update user: ${user.name}")

  override def prerequisites = Iterable(
    user.primaryGroup.map(GroupResource),
    user.home.map(FileResource),
    user.shell.map(FileResource)
  ).flatten

  override def consequences = Iterable(UserResource(user.name))

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean =
    commander.execute(resultsDir / "isActionCompleted", command.DoesUserExist(user))

  override def takeAction(commander: Commander, resultsDir: File): Unit =
    commander.execute(resultsDir / "takeAction", command.CreateOrUpdateUser(user))
}

case class DeleteUser(userName: String) extends CheckableMandate {
  override val description = Some(s"delete user: ${userName}")

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean =
    ! commander.execute(resultsDir / "isActionCompleted", command.DoesUserExist(User(userName)))

  override def takeAction(commander: Commander, resultsDir: File): Unit =
    commander.execute(resultsDir  / "takeAction", command.DeleteUser(userName))
}
