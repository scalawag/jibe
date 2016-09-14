package org.scalawag.jibe.backend

import java.io.File

trait Command {
  protected def getTestScript: String
  protected def getPerformScript: String

  def test(ssh: SSHConnectionInfo, dir: File) = {
    Sessions.get(ssh).execute(getTestScript, dir, ssh.sudo)
  }

  def perform(ssh: SSHConnectionInfo, dir: File) = {
    Sessions.get(ssh).execute(getPerformScript, dir, ssh.sudo)
  }
}
