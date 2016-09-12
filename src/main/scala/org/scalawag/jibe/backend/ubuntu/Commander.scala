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
        Command(mandate, createOrUpdateUser(user))
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

  private[this] def createOrUpdateUser(user: User) = {

    val commonOptions = mapify(
      user.uid.map( uid => "--uid" -> uid),
      user.comment.map( comment => "--comment" -> comment),
      user.primaryGroup.map( gid => "--gid" -> gid),
      user.shell.map( shell => "--shell" -> shell)
    )

    // usermod dumbly requires slightly different options than useradd

    val useraddOptions = commonOptions ++ mapify(
      user.home.map( home => "--home-dir" -> home),
      if ( user.system ) Some("--system" -> "") else None
    )

    val usermodOptions = commonOptions ++ mapify(
      user.home.map( home => "--home" -> home)
    )

    val useradd =
      s"""
         |PATH=/usr/sbin
         |useradd ${formatOptions(useraddOptions)} ${user.name}
      """.stripMargin.trim

    val usermod =
      if ( usermodOptions.isEmpty )
        s"""
           |if [ $$? == 9 ]; then
           |  exit 0
           |fi
        """.stripMargin.trim
      else
        s"""
           |if [ $$? == 9 ]; then
           |  usermod ${formatOptions(usermodOptions)} ${user.name}
           |fi
        """.stripMargin.trim

    Iterable(useradd,usermod).mkString("\n")
  }

  private[this] def mapify(raw: Iterable[(String, Any)]*): Map[String, String] = raw.flatten.toMap.mapValues(_.toString)

  private[this] def formatOptions(opts: Map[String, String]) = opts.map { case (k, v) => s"$k $v" }.mkString(" ")
}
