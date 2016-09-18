package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.{GroupResource, UserResource}

case class AddUserToGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"add user to groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList
}

case class RemoveUserFromGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = Iterable(UserResource(user))
}
