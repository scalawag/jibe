package org.scalawag.jibe

import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import FileUtils._
import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._
import org.scalawag.jibe.mandate.command.{User, Group}
import org.scalawag.timber.backend.receiver.formatter.timestamp.ISO8601TimestampFormatter
import Logging._
import org.scalawag.jibe.report.Model.Run

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Main {

  def main(args: Array[String]): Unit = {
    Logging // trigger initialization to get the logging configured

    val commanders = List(
      "192.168.212.11",
      "192.168.212.12",
      "192.168.212.13"
    ) map { ip =>
      new UbuntuCommander(SshInfo(ip, "vagrant", "vagrant", 22),sudo = true)
    }

    def CreateEveryoneUser(name: String) =
      new MandateSet(Some(s"create personal user: $name"), Seq(
        CreateOrUpdateUser(name),
        AddUserToGroups(name, "everyone"),
        CreateOrUpdateGroup("everyone")
      ))

    def AddUsersToGroup(group: String, users: String*) =
      new MandateSequence(Some(s"add multiple users to group $group"), users.map(AddUserToGroups(_, group)))

    val mandates1 = new MandateSet(Some("do everything"), Seq(
      CreateEveryoneUser("ernie"),
      CreateEveryoneUser("bert"),
      AddUsersToGroup("bedroom", "ernie", "bert"),
      CreateOrUpdateGroup(Group("bedroom", gid = Some(1064))),
      CreateOrUpdateGroup("grouch"),
      CreateOrUpdateUser(User("oscar", primaryGroup = Some("grouch"), home = Some("/tmp"), uid = Some(5005))),
      WriteRemoteFile(new File("/tmp/blah"), new File("build.sbt")),
      WriteRemoteFileFromTemplate(new File("/tmp/hello"), new File("hello.ssp"), Map("name" -> "count")),
      WriteRemoteFileFromTemplate(new File("/tmp/another"), "<%@ val noun: String %>\ntesting the ${noun}", Map("noun" -> "waters")),
      ExitWithArgument(34)
    ))

    val mandates2 = NoisyMandate

    def dumpMandate(pw: PrintWriter, mandate: Mandate, depth: Int = 0): Unit = {
      val prefix = "  " * depth

      mandate match {
        case cm: CompositeMandateBase =>
          val desc = s"${cm.description.getOrElse("<unnamed composite>")}"
          pw.println(prefix + desc)
          cm.mandates.foreach(dumpMandate(pw, _, depth + 1))
        case m =>
          pw.println(prefix + m.description.getOrElse(m.toString))
      }
    }

    val mandates4 = new MandateSet(Some("A"), Seq(
      ExitWithArgument(1),
      ExitWithArgument(2),
      new MandateSet(Some("B"), Seq(
        ExitWithArgument(3),
        ExitWithArgument(4)
      )),
      ExitWithArgument(5)
    ))

    try {
      val now = System.currentTimeMillis
      val df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS-'UTC'")
      val dateString = df.format(now)

      val runMandate = RunMandate(dateString, Seq(
        CommanderMandate(commanders(0), mandates1),
        CommanderMandate(commanders(1), mandates2)
//        ,
//        CommanderMandate(commanders(2), mandates1)
//        CommanderMandate(commanders(1), mandates4)
      ))

      val runDir = new File("results") / dateString


      // Mark this run directory's metadata (including schema version for backward compatibility)

      writeFileWithPrintWriter(runDir / "run.js") { pw =>
        pw.println(Run(1, new Date(now)).toJson.prettyPrint)
      }

      val job = MandateJob(runDir, runMandate, true)

      Executive.execute(job)

//      Reporter.generate(runDir)

    } catch {
      case ex: AbortException => // System.exit(1) - bad within sbt
    } finally {
      Sessions.shutdown
    }
  }
}
