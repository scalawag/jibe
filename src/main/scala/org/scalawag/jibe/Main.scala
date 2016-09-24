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

    val commanders = Iterable(
      "192.168.212.11",
      "192.168.212.12"
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

    val mandate = new CheckableCompositeMandate(None, Seq(
      CreateEveryoneUser("ernie"),
      CreateEveryoneUser("bert"),
      AddUsersToGroup("bedroom", "ernie", "bert"),
      CreateOrUpdateGroup(Group("bedroom", gid = Some(1064))),
      CreateOrUpdateUser(User("oscar", primaryGroup = Some("grouch"), home = Some("/tmp"), uid = Some(5005))),
      SendLocalFile(new File("build.sbt"), new File("/tmp/blah"))
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
      val orderedMandate = Orderer.order(mandate)

      log.debug { pw: PrintWriter =>
        pw.println("mandates before/after ordering:")
        dumpMandate(pw, mandate)
        pw.println("=" * 120)
        dumpMandate(pw, orderedMandate)
      }

      val date = ISO8601TimestampFormatter(TimeZone.getTimeZone("UTC")).format(System.currentTimeMillis)
      val runResultsDir = new File("results") / date
      val futures = commanders map { commander =>
        Future(Executive.takeActionIfNeeded(runResultsDir / "raw" / commander.toString, commander, orderedMandate))
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
