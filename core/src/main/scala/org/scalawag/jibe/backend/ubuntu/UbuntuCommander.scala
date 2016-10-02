package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate.command._

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror

class UbuntuCommander(ssh: SshInfo, sudo: Boolean = false) extends SecureShellBackend(ssh, sudo) with Commander {

  override def execute[A](command: Command[A])(implicit context: MandateExecutionContext): A = {
    import context.resultsDir

    def runScriptFor(args: Map[String, Any]): A = process(command) {
      execResource(context.log, command.getClass.getSimpleName + ".sh", args)
    }

    try {
      command match {

        case WriteRemoteFile(remotePath, content) =>
          process(command) {
            scp(context.log, content, remotePath)
          }

        case IsRemoteFileLength(file, length) =>
          runScriptFor(Map("length" -> length, "file" -> file))

        case IsRemoteFileMD5(file, md5) =>
          runScriptFor(Map("md5" -> md5, "file" -> file))

        case DoesGroupExist(group) =>
          runScriptFor(caseClassToContext("group", group))

        case CreateOrUpdateGroup(group) =>
          runScriptFor(caseClassToContext("group", group))

        case DeleteGroup(groupName) =>
          runScriptFor(Map("group" -> groupName))

        case DoesUserExist(user) =>
          runScriptFor(caseClassToContext("user", user))

        case CreateOrUpdateUser(user) =>
          runScriptFor(caseClassToContext("user", user))

        case DeleteUser(userName) =>
          runScriptFor(Map("userName" -> userName))

        // Note, these are all the same!!!
        case IsUserInAllGroups(user, groups) =>
          runScriptFor(Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case IsUserInAnyGroups(user, groups) =>
          runScriptFor(Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case AddUserToGroups(user, groups) =>
          runScriptFor(Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case RemoveUserFromGroups(user, groups) =>
          runScriptFor(Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case ExitWithArgument(ec) =>
          runScriptFor(Map("ec" -> ec))

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
