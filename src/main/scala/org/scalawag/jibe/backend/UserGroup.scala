package org.scalawag.jibe.backend

case class AddUserToGroups(user: String, groups: String*) extends Mandate {
  override def prerequisites = UserResource(user) :: groups.map(GroupResource).toList
}

case class RemoveUserFromGroups(user: String, groups: String*) extends Mandate {
  override def prerequisites = Iterable(UserResource(user))
}

case class AddUsersToGroup(group: String, users: String*) extends Mandate {
  override def prerequisites = GroupResource(group) :: users.map(UserResource).toList
}

case class RemoveUsersFromGroup(group: String, users: String*) extends Mandate {
  override def prerequisites = Iterable(GroupResource(group))
}
