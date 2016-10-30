package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._
import org.scalawag.jibe.backend.bash.BashCommander
import org.scalawag.jibe.mandate.MandateExecutionContext
import org.scalawag.jibe.mandate.command.CommandArgument.ToStructure
import org.scalawag.jibe.mandate.command._

case class UbuntuCommander(ssh: SshInfo, sudo: Boolean = false) extends SecureShellBackend(ssh, sudo) with Commander {

  override def execute[A](command: Command[A])(implicit context: MandateExecutionContext): A = {
    command match {
      case WriteRemoteFile(remotePath, content) =>
        process(command) {
          scp(context.log, content, remotePath)
        }

      case c: ToStructure => process(c) {
        execResource(context.log, command.getClass.getSimpleName + ".sh", BashCommander.bashify(c.toStructure))
      }

      case _ =>
        throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the command $command.")
    }
  }

  override val toString = s"${ssh.username}@${ssh.hostname}:${ssh.port} (ubuntu${if (sudo) ", sudo" else ""})"
}
