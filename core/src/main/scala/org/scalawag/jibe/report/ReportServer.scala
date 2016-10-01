package org.scalawag.jibe.report

import java.io.{File, FileFilter}
import akka.actor.ActorSystem
import spray.routing.{SimpleRoutingApp}
import spray.httpx.SprayJsonSupport

class ReportServer(port: Int = 8080, interface: String = "0.0.0.0") extends SimpleRoutingApp with SprayJsonSupport {
  private[this] implicit val system = ActorSystem()

  private[this] val results = new File("results")

  private[this] val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  def start: Unit = {
    startServer(interface, port) {
      pathPrefix("results") {
        getFromBrowseableDirectory("results")
      } ~
      pathPrefix("static") {
        getFromResourceDirectory("static")
      }
    }
  }

  def stop: Unit = {
    system.shutdown()
  }
}

object ReportServer extends SimpleRoutingApp with SprayJsonSupport {
  def main(args: Array[String]): Unit = {
    val rs =
      args.headOption match {
        case Some(n) => new ReportServer(n.toInt)
        case None => new ReportServer()
      }

    rs.start
  }
}
