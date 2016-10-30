package org.scalawag.jibe

import java.io.File

import FileUtils._
import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._
import org.scalawag.jibe.executive.{CommanderMultiTree, ExecutionPlan, Executive}
import org.scalawag.jibe.mandate._
import org.scalawag.jibe.mandate.command.{Group, User}
import org.scalawag.jibe.multitree.{MandateSequence, MandateSet}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

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
      MandateSet(s"create personal user: $name",
        CreateOrUpdateUser(name),
        AddUserToGroups(name, "everyone"),
        CreateOrUpdateGroup("everyone")
      )

    def AddUsersToGroup(group: String, users: String*) =
      MandateSequence(s"add multiple users to group $group", users.map(AddUserToGroups(_, group)):_*)

    val mandates1 = MandateSet("do everything",
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
    )

    val mandates2 = NoisyMandate

    val mandates4 = MandateSet("A",
      ExitWithArgument(1),
      ExitWithArgument(2),
      MandateSet("B",
        ExitWithArgument(3),
        ExitWithArgument(4)
      ),
      ExitWithArgument(5)
    )

    try {
      val commanderMultiTrees = Seq(
        CommanderMultiTree(commanders(0), mandates1),
        CommanderMultiTree(commanders(1), mandates2)
//        ,
//        CommanderMultiTree(commanders(2), mandates1)
//        CommanderMultiTree(commanders(1), mandates4)
      )

      val plan = new ExecutionPlan(commanderMultiTrees)

//      writeFileWithPrintWriter("graph.dot")(plan.toDot)

      Await.result(Executive.execute(plan, new File("results"), true)(ExecutionContext.global), Duration.Inf)
/*
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
*/
    } catch {
      case ex: AbortException => // System.exit(1) - bad within sbt
    } finally {
      Sessions.shutdown
    }
  }
}
