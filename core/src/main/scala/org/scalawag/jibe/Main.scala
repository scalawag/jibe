package org.scalawag.jibe

import java.io.{File, PrintWriter}

import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._
import org.scalawag.jibe.executive.{CommanderMultiTree, ExecutionPlan, Executive}
import org.scalawag.jibe.mandate._
import org.scalawag.jibe.mandate.command.{Group, User}
import org.scalawag.jibe.multitree._
import FileUtils._
import org.scalawag.jibe.report.Report.SUCCESS
import org.scalawag.jibe.report.cli.TextReport
import org.scalawag.jibe.report.cli.TextReport.TextReportOptions

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

object Main {

  // A few custom mandates for installing java software

  case class UpdateAptGet() extends StatelessMandate with MandateHelpers with OnlyIdentityEquals with CaseClassMandate {
    override val label = "update apt metadata"
    override def takeAction(implicit context: MandateExecutionContext) = runCommand(command.UpdateAptGet(0.seconds))
  }

  // Ad hoc scripting in a mandate
  val AcceptJava8License =
    new StatelessMandate with MandateHelpers {

      override val label = "Accept Java 8 license"
      override val fingerprint = MD5(this.getClass.getName)

      override def isActionCompleted(implicit context: MandateExecutionContext) =
      context.commander.executeBooleanScript(
      """PATH=/bin:/usr/bin
        |debconf-show oracle-java8-installer | grep '* shared/accepted-oracle-license-v1-1: true'
      """.stripMargin.trim
      )

      override def takeAction(implicit context: MandateExecutionContext) =
      context.commander.execute(
      """PATH=/bin:/usr/bin
        |echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
      """.stripMargin.trim
      )
    }

  val usesAptDatabase = CriticalSection(Semaphore(1, Some("apt")))
  val aptIndexDirty = new Flag(Some("apt index"))

  def InstallJava8(updateAptGet: MultiTreeLeaf = UpdateAptGet()) =
    MandateSequence("Install Java 8",
      WriteRemoteFile("/etc/apt/sources.list.d/webupd8team-java-trusty.list",
                      "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"),//.add(FlagOn(aptIndexDirty, SUCCESS)),
      InstallAptKey("keyserver.ubuntu.com", "7B2C3B0889BF5709A105D03AC2518248EEA14886"),//.add(FlagOn(aptIndexDirty, SUCCESS)),
      updateAptGet.add(usesAptDatabase),//.add(IfFlagged(aptIndexDirty)),
      AcceptJava8License,
      InstallPackage("oracle-java8-installer")//.add(usesAptDatabase)
    )

  def InstallSbt(updateAptGet: MultiTreeLeaf = UpdateAptGet()) =
    MandateSequence("Install sbt",
      WriteRemoteFile("/etc/apt/sources.list.d/sbt.list",
                      "deb https://dl.bintray.com/sbt/debian /"),//.add(FlagOn(aptIndexDirty, SUCCESS)),
      InstallAptKey("keyserver.ubuntu.com", "2EE0EA64E40A89B84B2DF73499E82A75642AC823"),//.add(FlagOn(aptIndexDirty, SUCCESS)),
      updateAptGet.add(usesAptDatabase),//.add(IfFlagged(aptIndexDirty)),
      InstallPackage("sbt")//.add(usesAptDatabase)
    )

  def main(args: Array[String]): Unit = {
    Logging.initialize()

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
      MandateSequence(s"add multiple users to group $group", users.map(AddUserToGroups(_, group):MultiTree):_*)

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

    val ex = ExitWithArgument(1)
    val mandates4 = MandateSet("A",
      ex,
      ExitWithArgument(2),
      MandateSet("B",
        ExitWithArgument(1),
        ExitWithArgument(4)
      ),
      ExitWithArgument(5),
      ex
    )

    val installPackagesWithSharedAptGetUpdate = {
      val updateAptGet = UpdateAptGet()
      MandateSet("install packages",
        InstallJava8(updateAptGet),
        InstallSbt(updateAptGet)
      )
    }

    val installPackagesWithoutSharedAptGetUpdate = {
      MandateSet("install packages",
        InstallJava8(),
        InstallSbt()
      )
    }

    try {
      val commanderMultiTrees = Seq(
        CommanderMultiTree(commanders(0), installPackagesWithSharedAptGetUpdate),
        CommanderMultiTree(commanders(1), installPackagesWithoutSharedAptGetUpdate)
      )

      val plan = new ExecutionPlan(commanderMultiTrees)

      FileUtils.writeFileWithPrintWriter("graph.dot")(plan.toDot)

      val runDir = Await.result(Executive.execute(plan, new File("results"), true)(ExecutionContext.global), Duration.Inf)

      val pw = new PrintWriter(System.out)
      val report = new TextReport(pw, runDir, TextReportOptions(errorLogs = true))

      report.renderReport()
      pw.flush()

    } catch {
      case ex: AbortException => // System.exit(1) - bad within sbt
    } finally {
      Sessions.shutdown
    }
  }
}
