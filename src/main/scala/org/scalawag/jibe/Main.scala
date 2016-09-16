package org.scalawag.jibe

import java.io.File
import java.util.TimeZone

import FileUtils._
import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._
import org.scalawag.jibe.mandate._
import org.scalawag.timber.backend.receiver.formatter.timestamp.ISO8601TimestampFormatter

object Main {

  def main(args: Array[String]): Unit = {
    val ssh = SSHConnectionInfo("localhost", "vagrant", "vagrant", 2222, sudo = true)

    def CreatePersonalUser(name: String) =
      CompositeMandate(s"create personal user: $name",
        CreateOrUpdateUser(name),
        CreateOrUpdateGroup("personal"),
        AddUserToGroups(name, "personal")
      )

    val mandate = CompositeMandate(
      AddUserToGroups("jack", "public") before CreateOrUpdateGroup("public"),
      CreateOrUpdateUser("robot"),
      CreateOrUpdateGroup("personal"),
      CreateOrUpdateUser(User("pope", primaryGroup = Some("personal"), home = Some("/tmp"), uid = Some(5005))),
      CreatePersonalUser("ernie"),
      CreatePersonalUser("bert"),
      SendLocalFile(new File("build.sbt"), new File("/tmp/blah"))
    )

    val orderedMandate = Orderer.orderMandate(mandate)

    val date = ISO8601TimestampFormatter(TimeZone.getTimeZone("UTC")).format(System.currentTimeMillis)
    val resultsDir = new File("results") / date
    val results = Executive.apply(orderedMandate, ssh, UbuntuCommander, resultsDir / "raw")

    // TODO: These results really shouldn't be buffered in memory, but how can we output them sensibly if they're not?
/*
    def dumpMandate(mandate: Mandate, depth: Int = 0): Unit = {
      val prefix = "  " * depth

      mandate match {
        case cm: CompositeMandate =>
          println(s"${prefix}CompositeMandate(${cm.description})")
          cm.mandates.foreach(dumpMandate(_, depth + 1))
        case m =>
          println(prefix + m)
      }
    }

    dumpMandate(orderedMandate)
*/
      /*
    val verbose = true

    def dumpResults(r: MandateResults, depth: Int = 0): Unit = {
      val prefix = "  " * depth

      if ( verbose )
        println(prefix + scala.Console.WHITE + "=" * ( 120 - depth * 2 ))

      val outcomeColor = r.outcome match {
        case MandateResults.Outcome.SUCCESS => Console.GREEN
        case MandateResults.Outcome.FAILURE => Console.RED
        case MandateResults.Outcome.USELESS => Console.YELLOW
      }
      println(prefix + outcomeColor + r.mandate.description.getOrElse("<unnamed sequence>"))

      r.innards match {
        case Left(innards) =>
          innards.foreach(dumpResults(_, depth + 1))
        case Right(cr) =>
          if ( verbose ) {
            val exitCodeColor =
              (cr.testResults.exitCode, cr.performResults.map(_.exitCode)) match {
                case (0, _) => Console.YELLOW
                case (_, Some(0)) => Console.GREEN
                case (_, _) => Console.RED
              }

            val tcmd = Source.fromString(cr.testResults.command).getLines

            println(prefix + scala.Console.WHITE + "-" * 120)
            tcmd.foreach( l => println(prefix + scala.Console.WHITE + l) )

            val tout = Source.fromString(cr.testResults.stdout).getLines
            val terr = Source.fromString(cr.testResults.stderr).getLines

            if ( tout.hasNext || terr.hasNext )
              println(prefix + scala.Console.WHITE + "-" * 120)
            terr.foreach( l => println(prefix + scala.Console.RED + l) )
            tout.foreach( l => println(prefix + scala.Console.CYAN + l) )

            val pcmd = cr.performResults.toIterable.map(_.command).map(Source.fromString).flatMap(_.getLines)

            println(prefix + scala.Console.WHITE + "-" * 120)
            pcmd.foreach( l => println(prefix + scala.Console.WHITE + l) )

            val pout = cr.performResults.map(_.stdout).map(Source.fromString).map(_.getLines)
            val perr = cr.performResults.map(_.stderr).map(Source.fromString).map(_.getLines)

            if ( pout.map(_.hasNext).getOrElse(false) || perr.map(_.hasNext).getOrElse(false) )
              println(prefix + scala.Console.WHITE + "-" * 120)
            perr.foreach(_.foreach( l => println(prefix + scala.Console.RED + l) ))
            pout.foreach(_.foreach( l => println(prefix + scala.Console.CYAN + l) ))
          }
      }
    }

    dumpResults(results)
*/
    Reporter.generate(resultsDir / "raw", resultsDir / "html" / "index.html")
    Sessions.shutdown
  }
}
