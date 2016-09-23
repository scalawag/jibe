package org.scalawag.jibe.mandate.command

import org.scalawag.jibe.mandate.User

/** Returns true if the user exists and has all of the specified attributes.
  * user.system is ignored.
  */

case class DoesUserExist(user: User) extends BooleanCommand

/** Returns true if the user specified exists on the system.  Does not indicate whether the user was already
  * present or was just created.  user.system is ignored unless the user must be created.
  */

case class CreateOrUpdateUser(user: User) extends UnitCommand

/** Returns true if the named user is not present on the system.  Does not indicate whether the user existed
  * prior to the call.
  */

case class DeleteUser(userName: String) extends UnitCommand
