package org.scalawag.jibe.backend.ubuntu

import java.io.File
import org.scalawag.jibe.backend.{Command, SSHConnectionInfo}
import org.scalawag.jibe.mandate.User

class CreateOrUpdateUserCommand(user: User) extends Command with BashCommands {

  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
    // These are where the parts we may care about live in the lines in /etc/passwd.  Primary group requires special
    // handling because it's a reference

    val conditions =
      List(
        user.uid -> 2,
        user.comment -> 4,
        user.home -> 5,
        user.shell -> 6
      ) flatMap { case (desiredOption, index) =>
        desiredOption map { desired =>
          s"""  if [ $${parts[$index]} != "$desired" ]; then exit 1; fi\n"""
        }
      }

    val groupCondition =
      user.primaryGroup map { groupName =>
        s"""  gname=$$( awk -F: '$$3 == '$${parts[3]}' { print $$1 }' /etc/group )
           |  if [ "$$gname" != "$groupName" ]; then exit 1; fi
""".stripMargin
      }

    val script =
      Seq(
        Some(
          s"""IFS=":" read -a parts <<< $$( grep ^${user.name}: /etc/passwd )
             |if [ $${#parts[*]} -eq 0 ]; then
             |  exit 1
             |else
""".stripMargin).toIterable,
        conditions,
        groupCondition.toIterable,
        Some(
          s"""  exit 0
             |fi""".stripMargin
        ).toIterable
      ).flatten.mkString
    ssh(sshConnectionInfo, script, dir)
  }

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
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

    val script = Iterable(useradd,usermod).mkString("\n")
    ssh(sshConnectionInfo, script, dir)
  }
}
