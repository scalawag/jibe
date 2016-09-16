package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._

trait BashCommands {
  protected[this] def mapify(raw: Iterable[(String, Any)]*): Map[String, String] = raw.flatten.toMap.mapValues(_.toString)

  protected[this] def formatOptions(opts: Map[String, String]) = opts.map { case (k, v) => s"$k $v" }.mkString(" ")
}

object UbuntuCommander extends Commander with BashCommands {

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
