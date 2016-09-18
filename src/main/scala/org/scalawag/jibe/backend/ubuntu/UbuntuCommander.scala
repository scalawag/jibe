package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._

object UbuntuCommander extends Commander {

  def getCommand(mandate: Mandate) = mandate match {
    case CreateOrUpdateUser(user) =>
      new CreateOrUpdateUserCommand(user)
//    case DeleteUser(name) =>
//      BasicCommand(s"userdel ${name}")
    case CreateOrUpdateGroup(group) =>
      new CreateOrUpdateGroupCommand(group)
//    case DeleteGroup(name) =>
//      BasicCommand(s"groupdel ${name}")
    case AddUserToGroups(user,groups@_*) =>
      new AddUserToGroupsCommand(user, groups:_*)
    case SendLocalFile(src, dst) =>
      new SendLocalFileCommand(src, dst)
    case _ =>
      throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the mandate $mandate.")
  }
}
