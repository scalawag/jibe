package org.scalawag.jibe.mandate

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.{Commander, GroupResource}

case class Group(name: String,
                 gid: Option[Int] = None,
                 system: Boolean = false)

object Group {
  implicit def fromString(name: String) = Group(name)
}

case class CreateOrUpdateGroup(group: Group) extends CheckableMandate {
  override val description = Some(s"update group: ${group.name}")

  override def consequences = Iterable(GroupResource(group.name))

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean =
    commander.execute(resultsDir / "isActionCompleted", command.DoesGroupExist(group))

  override def takeAction(commander: Commander, resultsDir: File): Unit =
    commander.execute(resultsDir / "takeAction", command.CreateOrUpdateGroup(group))
}

case class DeleteGroup(name: String) extends CheckableMandate {
  override val description = Some(s"update group: ${name}")

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean =
    ! commander.execute(resultsDir / "isActionCompleted", command.DoesGroupExist(Group(name)))

  override def takeAction(commander: Commander, resultsDir: File): Unit =
    commander.execute(resultsDir / "takeAction", command.DeleteGroup(name))
}