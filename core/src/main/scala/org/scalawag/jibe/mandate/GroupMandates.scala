package org.scalawag.jibe.mandate

import org.scalawag.jibe.mandate.command.Group
import org.scalawag.jibe.multitree._

/** Creates the group with the specified attributes (if it does not already exist) or modifies the existing group such
  * that its attributes match those specified.  Any optional members of the {@link Group} argument will be defaulted
  * (if the group is being created) or remain with their old values (if the group is being updated).
  *
  * @param group describes the group to be created.  Empty optional values are default or left unmodified.
  */

case class CreateOrUpdateGroup(group: Group) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"update group: ${group.name}"

  override val decorations = Set[MultiTreeDecoration](Consequences(GroupResource(group.name)))

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    runCommand(command.DoesGroupExist(group))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.CreateOrUpdateGroup(group))
}

case class DeleteGroup(name: String) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"update group: ${name}"

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    ! runCommand(command.DoesGroupExist(Group(name)))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.DeleteGroup(name))
}
