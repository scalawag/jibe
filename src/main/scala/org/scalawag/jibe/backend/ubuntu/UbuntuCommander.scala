package org.scalawag.jibe.backend.ubuntu

import java.io.{File, FileInputStream}

import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._

trait BashCommands {
  protected[this] def mapify(raw: Iterable[(String, Any)]*): Map[String, String] = raw.flatten.toMap.mapValues(_.toString)

  protected[this] def formatOptions(opts: Map[String, String]) = opts.map { case (k, v) => s"$k $v" }.mkString(" ")
}

class AddUserToGroupsCommand(user: String, group: String*) extends Command with BashCommands {

  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
    val script =
      s"""
         |existingGroups=$$( groups "$user" )
         |for testGroup in ${group.mkString(" ")}; do
         |  echo $$existingGroups | grep -w $$testGroup
         |  if [ $$? != 0 ]; then
         |    exit 1
         |  fi
         |done
         |
         |exit 0
       """.stripMargin
    ssh(sshConnectionInfo, script, dir)
  }

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
    val script = s"usermod -G ${group.mkString(",")} -a $user"
    ssh(sshConnectionInfo, script, dir)
  }
}

class CreateOrUpdateGroupCommand(group: Group) extends Command with BashCommands {

  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
    val elif =
      group.gid match {
        case Some(gid) =>
          s"""
             |elif [ "$$gid" != "$gid" ]; then
             |  exit 1
          """.stripMargin

        case None =>
          ""
      }

    val script =
      s"""
         |gid=$$( awk -F: '$$1 == "${group.name}" { print $$3 }' /etc/group )
         |if [ -z "$$gid" ]; then
         |  exit 1
         |$elif
         |else
         |  exit 0
         |fi
       """.stripMargin
    ssh(sshConnectionInfo, script, dir)
  }

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) = {

    val commonOptions = mapify(
      group.gid.map( gid => "--gid" -> gid)
    )

    val groupaddOptions = commonOptions ++ mapify(
      if ( group.system ) Some("--system" -> "") else None
    )

    val groupmodOptions = commonOptions

    val groupadd =
      s"""
         |PATH=/usr/sbin
         |groupadd ${formatOptions(groupaddOptions)} ${group.name}
      """.stripMargin.trim

    val groupmod =
      if ( groupmodOptions.isEmpty )
        s"""
           |if [ $$? == 9 ]; then
           |  exit 0
           |fi
        """.stripMargin.trim
      else
        s"""
           |if [ $$? == 9 ]; then
           |  groupmod ${formatOptions(groupmodOptions)} ${group.name}
           |fi
        """.stripMargin.trim

    val script = Iterable(groupadd,groupmod).mkString("\n")
    ssh(sshConnectionInfo, script, dir)
  }
}

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
        conditions.toIterable,
        groupCondition.toIterable,
        Some(
          s"""
             |  exit 0
             |fi
           """.stripMargin
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

class SendLocalFileCommand(src: File, dst: File) extends Command {

  // TODO: This could be sped up by checking for the presence of the file (and/or length) prior to calculating the MD5 locally.


  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
    // Calculate MD5 of local file
    val fis = new FileInputStream(src)
    val localMd5 =
      try {
        DigestUtils.md5Hex(fis).toLowerCase
      } finally {
        fis.close()
      }

    ssh(sshConnectionInfo, s"""test -r "$dst" && test $$( md5sum "$dst" | awk '{ print $$1 }' ) == $localMd5""", dir)
  }

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) = scp(sshConnectionInfo, src, dst, dir)
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
