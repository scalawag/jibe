package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._

case class Command(mandate: Mandate, command: String) {
  def perform(ssh: SSHConnectionInfo) = {
    Sessions.get(ssh).execute(command, true)
  }
}

object Commander {

  def apply(mandates: Iterable[Mandate], ssh: SSHConnectionInfo) = {

    def getCommand(mandate: Mandate) = mandate match {
      case CreateOrUpdateUser(user) =>
        Command(mandate, s"useradd ${user.name}")
      case DeleteUser(name) =>
        Command(mandate, s"userdel ${name}")
      case CreateOrUpdateGroup(group) =>
        Command(mandate, s"groupadd ${group.name}")
      case DeleteGroup(name) =>
        Command(mandate, s"groupdel ${name}")
      case AddUserToGroups(user,groups@_*) =>
        Command(mandate, s"usermod -G ${groups.mkString(",")} -a $user")
      case _ =>
        throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the mandate $mandate.")
    }

    val crs = mandates.map(getCommand).map(_.perform(ssh))
    MandateResults(crs.lastOption.map(_.exitCode).getOrElse(0),crs)
  }
}
