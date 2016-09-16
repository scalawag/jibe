package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.GroupResource

case class Group(name: String,
                 gid: Option[Int] = None,
                 system: Boolean = false)

object Group {
  implicit def fromString(name: String) = Group(name)
}

case class CreateOrUpdateGroup(group: Group) extends Mandate {
  override val description = Some(s"update group: ${group.name}")

  override def consequences = Iterable(GroupResource(group.name))
}

case class DeleteGroup(name: String) extends Mandate {
  override val description = Some(s"update group: ${name}")
}