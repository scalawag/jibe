package org.scalawag.jibe.backend.ubuntu

import java.io.File
import org.scalawag.jibe.backend.{Command, SSHConnectionInfo}

trait ScriptResourceCommand extends Command {
  protected def getScriptContext: Map[String, String]

  private[this] val scriptPrefix = this.getClass.getSimpleName

  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) =
    sshResource(sshConnectionInfo, s"${scriptPrefix}_test.sh", getScriptContext, dir)

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) =
    sshResource(sshConnectionInfo, s"${scriptPrefix}_perform.sh", getScriptContext, dir)
}
