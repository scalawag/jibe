package org.scalawag.jibe.backend.ubuntu

import java.io.File
import org.scalawag.jibe.backend.{Command, SSHConnectionInfo}
import org.scalawag.jibe.mandate.Group

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
