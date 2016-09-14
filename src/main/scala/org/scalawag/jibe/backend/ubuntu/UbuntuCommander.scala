package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._

trait BashCommands {
  protected[this] def mapify(raw: Iterable[(String, Any)]*): Map[String, String] = raw.flatten.toMap.mapValues(_.toString)

  protected[this] def formatOptions(opts: Map[String, String]) = opts.map { case (k, v) => s"$k $v" }.mkString(" ")
}

class AddUserToGroupsCommand(user: String, group: String*) extends Command with BashCommands {

  override protected def getTestScript = {
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
  }

  override protected def getPerformScript =
    s"usermod -G ${group.mkString(",")} -a $user"
}

class CreateOrUpdateGroupCommand(group: Group) extends Command with BashCommands {

  override protected def getTestScript = {
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

    s"""
       |gid=$$( awk -F: '$$1 == "${group.name}" { print $$3 }' /etc/group )
       |if [ -z "$$gid" ]; then
       |  exit 1
       |$elif
       |else
       |  exit 0
       |fi
     """.stripMargin
  }

  override protected def getPerformScript = {

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

    Iterable(groupadd,groupmod).mkString("\n")
  }
}

class CreateOrUpdateUserCommand(user: User) extends Command with BashCommands {

  override protected def getTestScript =
    s"""
       |echo one
       |echo two >& 2
       |echo three
       |exit 1
     """.stripMargin

  override protected def getPerformScript = {
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
}

case class BasicCommand(getPerformScript: String, getTestScript: String = "exit 1") extends Command

object UbuntuCommander extends Commander with BashCommands {

  def getCommand(mandate: Mandate) = mandate match {
    case CreateOrUpdateUser(user) =>
      new CreateOrUpdateUserCommand(user)
    case DeleteUser(name) =>
      BasicCommand(s"userdel ${name}")
    case CreateOrUpdateGroup(group) =>
      new CreateOrUpdateGroupCommand(group)
    case DeleteGroup(name) =>
      BasicCommand(s"groupdel ${name}")
    case AddUserToGroups(user,groups@_*) =>
      new AddUserToGroupsCommand(user, groups:_*)
    case _ =>
      throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the mandate $mandate.")
  }
}
