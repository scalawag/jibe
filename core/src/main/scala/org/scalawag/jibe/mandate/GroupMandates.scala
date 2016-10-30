package org.scalawag.jibe.mandate

import org.scalawag.jibe.mandate.command.Group
import org.scalawag.jibe.multitree._

object CreateOrUpdateGroup {
  /** Creates the group with the specified attributes (if it does not already exist) or modifies the existing group such
    * that its attributes match those specified.  Any optional members of the {@link Group} argument will be defaulted
    * (if the group is being created) or remain with their old values (if the group is being updated).
    *
    * @param group describes the group to be created.  Empty optional values are default or left unmodified.
    */

  case class CreateOrUpdateGroup(group: Group) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      runCommand(command.DoesGroupExist(group))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.CreateOrUpdateGroup(group))
  }

  def apply(group: Group) = MultiTreeLeaf(
    mandate = new CreateOrUpdateGroup(group),
    name = Some(s"update group: ${group.name}"),
    decorations = Set[MultiTreeDecoration](
      Consequences(GroupResource(group.name))
    )
  )
}

object DeleteGroup {
  case class DeleteGroup(name: String) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      ! runCommand(command.DoesGroupExist(Group(name)))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.DeleteGroup(name))
  }

  def apply(name: String) = MultiTreeLeaf(
    mandate = new DeleteGroup(name),
    name = Some(s"update group: ${name}")
  )
}
