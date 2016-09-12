package org.scalawag.jibe

import org.scalawag.jibe.backend.ubuntu.UbuntuCommander
import org.scalawag.jibe.backend._

import scala.io.Source

object Main {

  def main(args: Array[String]): Unit = {
    val ssh = SSHConnectionInfo("localhost", "vagrant", "vagrant", 2222, sudo = true)

    def CreatePersonalUserMandate(name: String) =
      CompositeMandate(s"create personal user: $name",
        CreateOrUpdateUser(name),
        CreateOrUpdateGroup("personal"),
        AddUserToGroups(name, "personal")
      )

    val mandate = CompositeMandate(
      AddUserToGroups("justin", "personal") before CreateOrUpdateGroup("public"),
      CreateOrUpdateUser("justin"),
      CreateOrUpdateGroup("personal"),
      CreateOrUpdateUser(User("pope", primaryGroup = Some("personal"), home = Some("/tmp"), uid = Some(5005))),
      CreatePersonalUserMandate("justin")
    )

    val orderedMandate = Orderer.orderMandate(mandate)

    val results = Executive.apply(orderedMandate, ssh, UbuntuCommander)

    // TODO: These results really shouldn't be buffered in memory, but how can we output them sensibly if they're not?

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

    val verbose = false

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
            val exitCodeColor = cr.exitCode match {
              case 0 => Console.GREEN
              case n => Console.RED
            }

            val out = Source.fromString(cr.stdout).getLines
            val err = Source.fromString(cr.stderr).getLines

            if ( out.hasNext || err.hasNext )
              println(prefix + scala.Console.WHITE + "-" * 120)

            println()
            err.foreach( l => println(prefix + scala.Console.RED + l) )
            out.foreach( l => println(prefix + scala.Console.CYAN + l) )
          }
      }
    }

    dumpResults(results)

    Sessions.shutdown
  }
}
