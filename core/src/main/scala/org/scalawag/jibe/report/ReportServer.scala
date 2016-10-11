package org.scalawag.jibe.report

import java.io.{File, FileFilter, PrintWriter}

import akka.actor.ActorSystem
import spray.routing.{Directive1, Route, SimpleRoutingApp}
import spray.httpx.SprayJsonSupport
import spray.json._
import Model._
import org.scalawag.jibe.Logging
import org.scalawag.timber.api.Logger
import spray.http.StatusCodes
import spray.util.LoggingContext
import org.scalawag.jibe.FileUtils._

import scala.util.Try
import spray.http.HttpHeaders.`Cache-Control`
import spray.http.CacheDirectives.{`max-age`, `no-cache`}

import scala.annotation.tailrec
import scala.io.Source
import Logging.log
import spray.httpx.marshalling.ToResponseMarshallable

class ReportServer(port: Int = 8080, interface: String = "0.0.0.0") extends SimpleRoutingApp with SprayJsonSupport {
  private[this] implicit val system = ActorSystem()
  private[this] implicit val loggingContext = new TimberLoggingContext(Logging.log)

  private[this] val results = new File("results")

  private[this] val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  private[this] def getIndexFromDirName(name: String) = {
    val firstPart = name.takeWhile(_.isDigit)
    if ( firstPart.forall(_ == '0') ) 0 else firstPart.dropWhile(_ == '0').toInt
  }

  private[this] def subdirFilter(n: Int) = new FileFilter {
    override def accept(f: File) = f.isDirectory && getIndexFromDirName(f.getName) == n
  }

  @tailrec
  private[this] def findDir(d: File, subDirIds: List[Int]): Option[File] = subDirIds match {
    case Nil => Some(d)
    case head :: tail =>
      // Find the directory in d that starts with the number in head
      d.listFiles(subdirFilter(head)).headOption match {
        case None => None
        case Some(x) => findDir(x, tail)
      }
  }

  private[this] def getSubdirMandateStatuses(dir: File, reverse: Boolean = false, limit: Int = Int.MaxValue, offset: Int = 0): Route =
    complete {
      if ( dir.exists ) {
        val subdirs = dir.listFiles(dirFilter).sortBy(_.getName)
        val orderedSubdirs = if ( reverse ) subdirs.reverse else subdirs
        // We can't determine the page yet because we don't know which of the subdirs have a mandate.js file in them.
        // Make it a stream to make sure we don't needlessly process all subdirs.
        val outStream = orderedSubdirs.toStream flatMap { dir =>
          val f = dir / "mandate.js"
          if ( f.exists ) {
            Some(Source.fromFile(f).mkString.parseJson.asJsObject)
          } else {
            None
          }
        }
        outStream.drop(offset).take(limit).toList
      } else {
        StatusCodes.NotFound
      }
    }

  private[this] val MandateId = """m0(?:_\d*)*""".r

  private[this] def mandateDir(runDir: File, mandateId: String): Directive1[File] = {
    log.debug(s"finding mandate directory for: $mandateId")
    val path = mandateId.split('_').tail.map(_.toInt).toList
    findDir(runDir, path) map { d =>
      log.debug(s"found: $d")
      provide(d)
    } getOrElse {
      log.debug("found nothing")
      reject
    }
  }

  def start: Unit = {
    startServer(interface, port) {
      path("stop") {
        complete {
          stop
          StatusCodes.NoContent
        }
      } ~
      pathPrefix("run") {
        respondWithHeader(`Cache-Control`(`no-cache`, `max-age`(0))) {
          pathEnd {
            parameters('limit.as[Int] ? 100, 'offset.as[Int] ? 0) { (limit, offset) =>
              getSubdirMandateStatuses(results, true, limit, offset)
            }
          } ~
          pathPrefix(Segment) { runId =>
            provide(results / runId) { runDir =>
              path("run") {
                getFromFile( runDir / "run.js" )
              } ~
              pathPrefix(MandateId) { mandateId =>
                mandateDir(runDir, mandateId) { mandateDir =>
                  path("children") {
                    getSubdirMandateStatuses(mandateDir)
                  } ~
                  path("status") {
                    getFromFile( mandateDir / "mandate.js" )
                  } ~
                  path("log") {
                    getFromFile( mandateDir / "log" )
                  }
                }
              } ~
              pathSingleSlash {
                getFromResource("web/run.html")
              }
            }
          }
        }
      } ~
      pathEndOrSingleSlash {
        getFromResource("web/index.html")
      } ~
      pathPrefix("static") {
        getFromResourceDirectory("web/static")
      }
    }
  }

  def stop: Unit = {
    system.shutdown()
  }

  class TimberLoggingContext(log: Logger) extends LoggingContext {
    override val isDebugEnabled = true
    override val isInfoEnabled = true
    override val isWarningEnabled = true
    override val isErrorEnabled = true

    override protected def notifyDebug(message: String) = log.debug(message)
    override protected def notifyInfo(message: String) = log.info(message)
    override protected def notifyWarning(message: String) = log.warn(message)
    override protected def notifyError(message: String) = log.error(message)
    override protected def notifyError(cause: Throwable, message: String) = log.error { pw: PrintWriter =>
      pw.println(message)
      cause.printStackTrace(pw)
    }
  }
}

object ReportServer {
  def main(args: Array[String]): Unit = {
    val rs =
      args.headOption match {
        case Some(n) => new ReportServer(n.toInt)
        case None => new ReportServer()
      }

    rs.start
  }
}

/*
run/0-1-1/
run/mandatePath/status -> JSObject
run/mandatePath/children -> JSObject[ id -> JSObject]
run/mandatePath/log
 */