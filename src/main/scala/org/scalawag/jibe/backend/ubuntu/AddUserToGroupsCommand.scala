package org.scalawag.jibe.backend.ubuntu

import java.io.File
import org.scalawag.jibe.backend.{Command, SSHConnectionInfo}

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
