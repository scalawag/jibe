package org.scalawag.jibe

import java.io.{File, PrintWriter}
import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._
import org.scalawag.jibe.mandate.command.{User, Group}

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
      InstallPackage(Package("vim")),
      ExitWithArgument(34)
    ))

    val mandates2 = NoisyMandate

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
      val runMandate = RunMandate(Seq(
        CommanderMandate(commanders(0), mandates1),
        CommanderMandate(commanders(1), mandates2)
//        ,
//        CommanderMandate(commanders(2), mandates1)
//        CommanderMandate(commanders(1), mandates4)
      ))

      val job = Executive.execute(new File("results"), runMandate, true)

      {
        import org.scalawag.jibe.report.ExecutiveStatus._
        def color(s: Value) = s match {
          case FAILURE => Console.RED
          case BLOCKED => Console.MAGENTA
          case SUCCESS => Console.GREEN
          case NEEDED => Console.CYAN
          case UNNEEDED => Console.YELLOW
        }

        println("Overall run: " + color(job.executiveStatus) + job.executiveStatus)
        val leafStatusCounts = ParentMandateJob.getChildLeafStatusCounts(job.status)
        Seq(UNNEEDED, NEEDED, SUCCESS, FAILURE, BLOCKED) foreach { s =>
          leafStatusCounts.get(s) foreach { n =>
            println(s"  ${color(s)}${s}: $n")
          }
        }
      }

    } catch {
      case ex: AbortException => // System.exit(1) - bad within sbt
    } finally {
      Sessions.shutdown
    }
  }
}
