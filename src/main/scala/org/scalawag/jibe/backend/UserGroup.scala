package org.scalawag.jibe.backend

case class AddUserToGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"add user to groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList
}

case class RemoveUserFromGroups(user: String, groups: String*) extends Mandate {
  override val description = Some(s"remove user from groups: $user -> ${groups.mkString(" ")}")
  override def prerequisites = Iterable(UserResource(user))
}

case class AddUsersToGroup(group: String, users: String*) extends Mandate {
  override val description = Some(s"add users to group: $group <- ${users.mkString(" ")}")
  override def prerequisites = GroupResource(group) :: users.map(UserResource).toList
}

case class RemoveUsersFromGroup(group: String, users: String*) extends Mandate {
  override val description = Some(s"remove users from group: $group <- ${users.mkString(" ")}")
  override def prerequisites = Iterable(GroupResource(group))
}
