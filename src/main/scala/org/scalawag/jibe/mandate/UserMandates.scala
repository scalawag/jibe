package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.{FileResource, GroupResource, UserResource}

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

case class CreateOrUpdateUser(user: User) extends Mandate {
  override val description = Some(s"update user: ${user.name}")

  override def prerequisites = Iterable(
    user.primaryGroup.map(GroupResource),
    user.home.map(FileResource),
    user.shell.map(FileResource)
  ).flatten

  override def consequences = Iterable(UserResource(user.name))
}

case class DeleteUser(name: String) extends Mandate {
  override val description = Some(s"delete user: ${name}")
}
