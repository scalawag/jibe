package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._

case class Command(mandate: Mandate, command: String) {
  def perform(ssh: SSHConnectionInfo) = {
    Sessions.get(ssh).execute(command)
  }
}

object Commander {

  def apply(mandates: Iterable[Mandate], ssh: SSHConnectionInfo) = {

    val sudo = if ( ssh.sudo ) "sudo" else ""

    def getCommand(mandate: Mandate) = mandate match {
      case CreateOrUpdateUser(user) =>
        Command(mandate, s"$sudo useradd ${user.name}")
      case DeleteUser(name) =>
        Command(mandate, s"$sudo userdel ${name}")
      case CreateOrUpdateGroup(group) =>
        Command(mandate, s"$sudo groupadd ${group.name}")
      case DeleteGroup(name) =>
        Command(mandate, s"$sudo groupdel ${name}")
      case AddUserToGroups(user,groups@_*) =>
        Command(mandate, s"$sudo usermod -G ${groups.mkString(",")} -a $user")
      case _ =>
        throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the mandate $mandate.")
    }

    val crs = mandates.map(getCommand).map(_.perform(ssh))
    MandateResults(crs.lastOption.map(_.exitCode).getOrElse(0),crs)
  }
}
