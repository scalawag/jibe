package org.scalawag.jibe.backend.ubuntu

import java.io.File

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate.MandateExecutionContext
import org.scalawag.jibe.mandate.command._

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror

class UbuntuCommander(ssh: SshInfo, sudo: Boolean = false) extends SecureShellBackend(ssh, sudo) with Commander {

  override def execute(context: MandateExecutionContext, command: Command): Int = {
    import context.resultsDir

    def runScriptFor(command: Command, args: Map[String, Any]) = {
      context.log.info(MandateExecutionLogging.CommandStart)(command.toString)
      val ec = execResource(context, command.getClass.getSimpleName + ".sh", args)
      context.log.info(MandateExecutionLogging.CommandExit)(ec.toString)
      ec
    }

    try {
      command match {

        case SendLocalFile(local, remote) => scp(context, local, remote)

        case IsRemoteFileLength(file, length) =>
          runScriptFor(command, Map("llen" -> length, "rpath" -> file))

        case IsRemoteFileMD5(file, md5) =>
          runScriptFor(command, Map("lmd5" -> md5, "rpath" -> file))

        case DoesGroupExist(group) =>
          runScriptFor(command, caseClassToContext("group", group))

        case CreateOrUpdateGroup(group) =>
          runScriptFor(command, caseClassToContext("group", group))

        case DeleteGroup(groupName) =>
          runScriptFor(command, Map("group" -> groupName))

        case DoesUserExist(user) =>
          runScriptFor(command, caseClassToContext("user", user))

        case CreateOrUpdateUser(user) =>
          runScriptFor(command, caseClassToContext("user", user))

        case DeleteUser(userName) =>
          runScriptFor(command, Map("user" -> userName))

        // Note, these are all the same!!!
        case IsUserInAllGroups(user, groups) =>
          runScriptFor(command, Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case IsUserInAnyGroups(user, groups) =>
          runScriptFor(command, Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case AddUserToGroups(user, groups) =>
          runScriptFor(command, Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case RemoveUserFromGroups(user, groups) =>
          runScriptFor(command, Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case ExitWithArgument(ec) =>
          runScriptFor(command, Map("ec" -> ec))

        case _ =>
          throw new RuntimeException(s"Commander ${this.getClass.getName} does not support the command $command.")

      }
    } catch {
      case ex: Exception =>
        writeFileWithPrintWriter(resultsDir / "exception") { pw =>
          ex.printStackTrace(pw)
        }
        throw ex
    }
  }

  protected def caseClassToContext[T: ClassTag](name: String, cc: T) = {
    val mirror = currentMirror.reflect(cc)
    mirror.symbol.typeSignature.members.collect {
      case s: TermSymbol if s.isCaseAccessor => mirror.reflectField(s)
    } map { r =>

      def stringify(a: Any): String =
        a match {
          case s: String => s
          case n: Int => n.toString
          case b: Boolean => if ( b ) "t" else ""
          case i: Iterable[_] => i.map(stringify).mkString(" ")
          case Some(x) => stringify(x)
          case None => ""
        }

      s"${name}_${r.symbol.name.toString.trim}" -> stringify(r.get)
    } toMap
  }

  override val toString = s"${ssh.username}@${ssh.hostname}:${ssh.port} (ubuntu${if (sudo) ", sudo" else ""})"
}
