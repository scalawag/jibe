package org.scalawag.jibe.backend.ubuntu

import java.io.File

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate.command._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror

class UbuntuCommander(ssh: SshInfo, sudo: Boolean = false) extends SecureShellBackend(ssh, sudo) with Commander {

  override def execute(resultsDir: File, command: Command): Int =
    try {
      command match {

        case SendLocalFile(local, remote) => scp(resultsDir, local, remote)

        case IsRemoteFileLength(file, length) =>
          execResource(resultsDir, scriptFor(command), Map("llen" -> length, "rpath" -> file))

        case IsRemoteFileMD5(file, md5) =>
          execResource(resultsDir, scriptFor(command), Map("lmd5" -> md5, "rpath" -> file))

        case DoesGroupExist(group) =>
          execResource(resultsDir, scriptFor(command), caseClassToContext("group", group))

        case CreateOrUpdateGroup(group) =>
          execResource(resultsDir, scriptFor(command), caseClassToContext("group", group))

        case DeleteGroup(groupName) =>
          execResource(resultsDir, scriptFor(command), Map("group" -> groupName))

        case DoesUserExist(user) =>
          execResource(resultsDir, scriptFor(command), caseClassToContext("user", user))

        case CreateOrUpdateUser(user) =>
          execResource(resultsDir, scriptFor(command), caseClassToContext("user", user))

        case DeleteUser(userName) =>
          execResource(resultsDir, scriptFor(command), Map("user" -> userName))

        // Note, these are all the same!!!
        case IsUserInAllGroups(user, groups) =>
          execResource(resultsDir, scriptFor(command), Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case IsUserInAnyGroups(user, groups) =>
          execResource(resultsDir, scriptFor(command), Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case AddUserToGroups(user, groups) =>
          execResource(resultsDir, scriptFor(command), Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

        case RemoveUserFromGroups(user, groups) =>
          execResource(resultsDir, scriptFor(command), Map("targetUser" -> user, "targetGroups" -> groups.mkString(" ")))

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

  protected def scriptFor(any: Any) = any.getClass.getSimpleName + ".sh"

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
