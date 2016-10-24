package org.scalawag.jibe.report

import java.io.{File, FileFilter, PrintWriter}

import akka.actor.ActorSystem
import spray.routing._
import spray.httpx.SprayJsonSupport
import spray.json._
import JsonFormat._
import org.scalawag.jibe.Logging
import org.scalawag.timber.api.Logger
import spray.http._
import spray.util.LoggingContext
import org.scalawag.jibe.FileUtils._
import spray.http.HttpHeaders.`Cache-Control`
import spray.http.CacheDirectives.{`max-age`, `no-cache`}

import scala.annotation.tailrec
import scala.io.Source
import Logging.log

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

  private[this] def getSubdirsWithMandateStatus(dir: File, reverse: Boolean = false) = {
    val subdirs = dir.listFiles(dirFilter).sortBy(_.getName)
    val orderedSubdirs = if ( reverse ) subdirs.reverse else subdirs
    // We can't determine the page yet because we don't know which of the subdirs have a mandate.js file in them.
    // Make it a stream to make sure we don't needlessly process all subdirs.
    orderedSubdirs.toStream flatMap { dir =>
      val f = dir / "mandate.js"
      if ( f.exists ) {
        Some(dir -> Source.fromFile(f).mkString.parseJson.convertTo[MandateStatus])
      } else {
        None
      }
    }
  }

  private[this] def getRuns(limit: Int = Int.MaxValue, offset: Int = 0): Route =
    complete {
      if ( results.exists ) {
        getSubdirsWithMandateStatus(results, true).flatMap { case (subdir, ms) =>
          val runFile = subdir / "run.js"
          if ( runFile.exists ) {
            val run = Source.fromFile(runFile).mkString.parseJson.convertTo[Run]
            Some(run.copy(mandate = Some(ms)))
          } else {
            None
          }
        }.drop(offset).take(limit).toList
      } else {
        StatusCodes.NotFound
      }
    }

  private[this] def getChildMandateStatuses(dir: File): Route =
    complete {
      if ( dir.exists ) {
        getSubdirsWithMandateStatus(dir).map(_._2).toList
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

  private[this] def runDir(runId: String): Directive1[File] = {
    val runDir = results / runId
    if ( runDir.exists ) {
      provide(runDir)
    } else {
      reject
    }
  }

  /* I couldn't use the built-in Range handling because the semantics didn't quite match up.  Specifically, it's not
   * designed for files that are growing.  The If-Modified-Since and If-Modified headers take precedence over the
   * Range header.  So, if the browser gets a partial response and stores the conditional headers (ETag and
   * Last-Modified), the next time it requests a different byte range, it will get a NotModified response even though
   * it's asking for a range that it hasn't seen.
   *
   * This way, our semantics exactly match our client's goals.  If there are new bytes, they will be returned.  This
   * also prevents a ton of error logging in the browser from not-yet-written logs (404s) and completely-written
   * logs (416s).
   */

  private[this] def getLogFileFrom(mandateDir: File, offset: Long = 0)(implicit settings: RoutingSettings): Route =
    provide(mandateDir / "log") { logFile =>
      detach() {
        autoChunk(settings.fileChunkingThresholdSize, settings.fileChunkingChunkSize) {
          complete {
            if ( logFile.exists ) {
              if (offset < logFile.length)
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, HttpData(logFile, offset))
              else if (offset == logFile.length)
                (StatusCodes.NoContent, "log file contains no new bytes")
              else
                (StatusCodes.BadRequest, "log file does not contain that many bytes")
            } else {
              (StatusCodes.NoContent, "log file does not yet exist")
            }
          }
        }
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
      pathPrefix("data") {
        respondWithHeader(`Cache-Control`(`no-cache`, `max-age`(0))) {
          path("runs") {
            parameters('limit.as[Int] ? 100, 'offset.as[Int] ? 0) { (limit, offset) =>
              getRuns(limit, offset)
            }
          } ~
          pathPrefix("run" / Segment) { runId =>
            runDir(runId) { runDir =>
              path("run") {
                getFromFile( runDir / "run.js" )
              } ~
              pathPrefix(MandateId) { mandateId =>
                mandateDir(runDir, mandateId) { mandateDir =>
                  path("children") {
                    getChildMandateStatuses(mandateDir)
                  } ~
                  path("status") {
                    getFromFile( mandateDir / "mandate.js" )
                  } ~
                  pathPrefix("log") {
                    pathEnd {
                      getLogFileFrom(mandateDir, 0)
                    } ~
                    path(LongNumber) { offset =>
                      getLogFileFrom(mandateDir, offset)
                    }
                  }
                }
              }
            }
          }
        }
      } ~
      pathEndOrSingleSlash {
        getFromResource("web/index.html")
      } ~
      path("latest" /) {
        getFromResource("web/run.html")
      } ~
      path(Segment /) { runId =>
        runDir(runId) { _ => // only allowed if the run exists
          getFromResource("web/run.html")
        }
      } ~
      path("style.css") {
        respondWithMediaType(MediaTypes.`text/css`) & complete(CSS.rendered)
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
