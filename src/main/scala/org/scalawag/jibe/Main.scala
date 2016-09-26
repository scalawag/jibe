package org.scalawag.jibe

import java.io.{File, PrintWriter}
import java.util.TimeZone

import FileUtils._
import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._
import org.scalawag.timber.backend.receiver.formatter.timestamp.ISO8601TimestampFormatter
import Logging._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Main {

  def main(args: Array[String]): Unit = {

    val commanders = List(
      "192.168.212.11",
      "192.168.212.12",
      "192.168.212.13"
    ) map { ip =>
      new UbuntuCommander(SshInfo(ip, "vagrant", "vagrant", 22),sudo = true)
    }

    def CreateEveryoneUser(name: String) =
      new CheckableCompositeMandate(Some(s"create personal user: $name"), Seq(
        CreateOrUpdateUser(name),
        CreateOrUpdateGroup("everyone"),
        AddUserToGroups(name, "everyone")
      ))

    def AddUsersToGroup(group: String, users: String*) =
      new CheckableCompositeMandate(Some(s"add multiple users to group $group"), users.map(AddUserToGroups(_, group)))

    val mandates1 = new CheckableCompositeMandate(None, Seq(
      CreateEveryoneUser("ernie"),
      CreateEveryoneUser("bert"),
      AddUsersToGroup("bedroom", "ernie", "bert"),
      CreateOrUpdateGroup(Group("bedroom", gid = Some(1064))),
      CreateOrUpdateUser(User("oscar", primaryGroup = Some("grouch"), home = Some("/tmp"), uid = Some(5005))),
      WriteRemoteFile(new File("/tmp/blah"), new File("build.sbt")),
      WriteRemoteFileFromTemplate(new File("/tmp/hello"), new File("hello.ssp"), Map("name" -> "count"))
//    ,
//      ExitWithArgument(34)
    ))

    val mandates2 = new CheckableCompositeMandate(None, Seq(
      NoisyMandate
    ))

    def dumpMandate(pw: PrintWriter, mandate: Mandate, depth: Int = 0): Unit = {
      val prefix = "  " * depth

      mandate match {
        case cm: CompositeMandate =>
          val desc = s"${ if ( cm.fixedOrder ) "[FIXED] " else "" }${cm.description.getOrElse("<unnamed composite>")}"
          pw.println(prefix + desc)
          cm.mandates.foreach(dumpMandate(pw, _, depth + 1))
        case m =>
          pw.println(prefix + m.description.getOrElse(m.toString))
      }
    }

    try {
      val orderedMandate = Orderer.order(mandates1)

      log.debug { pw: PrintWriter =>
        pw.println("mandates before/after ordering:")
        dumpMandate(pw, mandates1)
        pw.println("=" * 120)
        dumpMandate(pw, orderedMandate)
      }

      val mandateMap = Map(
        commanders(0) -> orderedMandate,
        commanders(1) -> mandates2,
        commanders(2) -> mandates1
      )

      val date = ISO8601TimestampFormatter(TimeZone.getTimeZone("UTC")).format(System.currentTimeMillis)
      val runResultsDir = new File("results") / date
      val futures = mandateMap map { case (commander, mandate) =>
        Future(Executive.takeActionIfNeeded(runResultsDir / "raw" / commander.toString, commander, mandate))
      }

      Await.ready(Future.sequence(futures), Duration.Inf) // TODO: eventually go all asynchronous?

      Reporter.generate(runResultsDir)
    } catch {
      case ex: AbortException => // System.exit(1) - bad within sbt
    } finally {
      Sessions.shutdown
    }
  }
}
