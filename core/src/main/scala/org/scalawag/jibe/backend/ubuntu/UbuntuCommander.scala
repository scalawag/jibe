package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.backend._
import org.scalawag.jibe.backend.bash.BashCommander
import org.scalawag.jibe.mandate.command.CommandArgument.ToStructure
import org.scalawag.jibe.mandate.command._
import org.scalawag.jibe.multitree.MandateExecutionContext

case class UbuntuCommander(ssh: SshInfo, sudo: Boolean = false) extends SecureShellBackend(ssh, sudo) with Commander {

  override def execute[A](command: Command[A])(implicit context: MandateExecutionContext): A =
    interpretExitCodeCommand(command) {
      command match {
        case WriteRemoteFile(remotePath, content) =>
          scp(context.log, content, remotePath)

        case c: ToStructure =>
          execResource(context.log, command.getClass.getSimpleName + ".sh", BashCommander.bashify(c.toStructure))

        case _ =>
          throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the command $command.")
      }
    }

  override def executeBooleanScript(script: String, description: String = "")(implicit context: MandateExecutionContext) =
    interpretExitCodeBoolean(description) {
      exec(context.log, script)
    }

  override def executeIntScript(script: String, description: String = "")(implicit context: MandateExecutionContext) =
    interpretExitCodeInt(description) {
      exec(context.log, script)
    }

  override def execute(script: String, description: String = "")(implicit context: MandateExecutionContext) =
    interpretExitCodeUnit(description) {
      exec(context.log, script)
    }

  override val toString = s"${ssh.username}@${ssh.hostname}:${ssh.port} (ubuntu${if (sudo) ", sudo" else ""})"
}
