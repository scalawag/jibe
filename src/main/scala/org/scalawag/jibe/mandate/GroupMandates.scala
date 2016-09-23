package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.GroupResource

case class Group(name: String,
                 gid: Option[Int] = None,
                 system: Boolean = false)

object Group {
  implicit def fromString(name: String) = Group(name)
}

case class CreateOrUpdateGroup(group: Group) extends CheckableMandate {
  override val description = Some(s"update group: ${group.name}")

  override def consequences = Iterable(GroupResource(group.name))

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean =
    runCommand("isActionCompleted", command.DoesGroupExist(group))

  override def takeAction(implicit context: MandateExecutionContext): Unit =
    runCommand("takeAction", command.CreateOrUpdateGroup(group))
}

case class DeleteGroup(name: String) extends CheckableMandate {
  override val description = Some(s"update group: ${name}")

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean =
    ! runCommand("isActionCompleted", command.DoesGroupExist(Group(name)))

  override def takeAction(implicit context: MandateExecutionContext): Unit =
    runCommand("takeAction", command.DeleteGroup(name))
}