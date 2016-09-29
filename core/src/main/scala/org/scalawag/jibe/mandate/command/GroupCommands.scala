package org.scalawag.jibe.mandate.command

import org.scalawag.jibe.mandate.Group

/** Returns true if the group exists and has all of the specified attributes.
  * group.system is ignored.
  */

case class DoesGroupExist(group: Group) extends BooleanCommand

/** Returns without exception if the group specified exists on the system with the specified parameters.  Does not
  * indicate whether the group was already present or was just created.  group.system is ignored unless the group
  * must be created.
  */

case class CreateOrUpdateGroup(group: Group) extends UnitCommand

/** Returns without exception if the named group is not present on the system after the command is run.  Does not
  * indicate whether the group existed prior to the call.
  */

case class DeleteGroup(groupName: String) extends UnitCommand
