package org.scalawag.jibe.mandate.command

@CommandArgument
case class User(name: String,
                primaryGroup: Option[String] = None,
                uid: Option[Int] = None,
                home: Option[String] = None,
                shell: Option[String] = None,
                comment: Option[String] = None,
                system: Boolean = false)

object User {
  implicit def fromString(name: String) = User(name)
}

/** Returns true if the user exists and has all of the specified attributes.
  * user.system is ignored.
  */

@CommandArgument
case class DoesUserExist(user: User) extends BooleanCommand

/** Returns true if the user specified exists on the system.  Does not indicate whether the user was already
  * present or was just created.  user.system is ignored unless the user must be created.
  */

@CommandArgument
case class CreateOrUpdateUser(user: User) extends UnitCommand

/** Returns true if the named user is not present on the system.  Does not indicate whether the user existed
  * prior to the call.
  */

@CommandArgument
case class DeleteUser(userName: String) extends UnitCommand
