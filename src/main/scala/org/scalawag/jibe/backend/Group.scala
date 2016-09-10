package org.scalawag.jibe.backend

case class Group(name: String,
                 uid: Option[Int] = None,
                 system: Boolean = false)

object Group {
  implicit def fromString(name: String) = Group(name)
}

case class CreateOrUpdateGroup(group: Group) extends Mandate {
  override def consequences = Iterable(GroupResource(group.name))
}

case class DeleteGroup(name: String) extends Mandate