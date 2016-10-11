package org.scalawag.jibe.mandate.command

@CommandArgument
case class Group(name: String,
                 gid: Option[Int] = None,
                 system: Boolean = false)

object Group {
  implicit def fromString(name: String) = Group(name)
}

/** Returns true if the group exists and has all of the specified attributes.
  * group.system is ignored.
  */

@CommandArgument
case class DoesGroupExist(group: Group) extends BooleanCommand

/** Returns without exception if the group specified exists on the system with the specified parameters.  Does not
  * indicate whether the group was already present or was just created.  group.system is ignored unless the group
  * must be created.
  */

@CommandArgument
case class CreateOrUpdateGroup(group: Group) extends UnitCommand

/** Returns without exception if the named group is not present on the system after the command is run.  Does not
  * indicate whether the group existed prior to the call.
  */

@CommandArgument
case class DeleteGroup(groupName: String) extends UnitCommand
