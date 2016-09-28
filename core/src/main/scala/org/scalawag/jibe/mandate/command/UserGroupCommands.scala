package org.scalawag.jibe.mandate.command

import org.scalawag.jibe.mandate.User

/** Returns true if the user exists and is a member of all of the specified groups, all of which must exist. */

case class IsUserInAllGroups(user: String, groups: Iterable[String]) extends BooleanCommand

/** Returns true if the user exists and is a member of any of the specified groups, at least one of which must exist. */

case class IsUserInAnyGroups(user: String, groups: Iterable[String]) extends BooleanCommand

/** Returns true if the user is a member of all the specified groups. */

case class AddUserToGroups(user: String, groups: Iterable[String]) extends UnitCommand

/** Returns true if the user is a member of none of the specified groups. */

case class RemoveUserFromGroups(user: String, groups: Iterable[String]) extends UnitCommand
