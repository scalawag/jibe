package org.scalawag.jibe

import org.scalawag.jibe.backend.ubuntu.Commander
import org.scalawag.jibe.backend._

import scala.io.Source

object Main {

  def main(args: Array[String]): Unit = {
    val ssh = SSHConnectionInfo("localhost", "vagrant", "vagrant", 2222, sudo = true)

    val CreatePersonalUserMandate = CompositeMandate(
      CreateOrUpdateUser("justin"),
      CreateOrUpdateGroup("personal"),
      AddUserToGroups("justin", "personal")
    )

    val mandates = Iterable(
      AddUserToGroups("justin", "personal") before CreateOrUpdateGroup("public"),
      CreateOrUpdateUser("justin"),
      CreateOrUpdateGroup("personal"),
      CreatePersonalUserMandate
    )

    val orderedMandates = Orderer.order(mandates)

    val results = Commander.apply(orderedMandates, ssh)

    results.commandResults.foreach { r =>
      val dispColor = if ( r.exitCode == 0 ) Console.GREEN else Console.RED

      println(scala.Console.WHITE + "=" * 120)
      println(scala.Console.WHITE + r.command + dispColor + " => " + r.exitCode)

      val out = Source.fromString(r.stdout).getLines
      val err = Source.fromString(r.stderr).getLines

      if ( out.hasNext || err.hasNext )
        println(scala.Console.WHITE + "-" * 120)

      err.foreach( l => println(scala.Console.RED + l) )
      out.foreach( l => println(scala.Console.CYAN + l) )
    }

    Sessions.shutdown
  }
}
