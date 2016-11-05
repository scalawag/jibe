package org.scalawag.jibe.report

import java.io.{File, FileFilter, PrintWriter}
import org.scalawag.jibe.FileUtils._
import akka.actor.ActorSystem
import spray.routing._
import spray.httpx.SprayJsonSupport
import spray.json._
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
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report.JsonFormats._

class ReportServer(port: Int = 8080, interface: String = "0.0.0.0") extends SimpleRoutingApp with SprayJsonSupport {
  private[this] implicit val system = ActorSystem()
  private[this] implicit val loggingContext = new TimberLoggingContext(Logging.log)

  // For backward compatibility with the old UI

  case class MandateStatus(id: String,
//                           mandate: String,
                           description: Option[String],
                           composite: Boolean,
                           startTime: Option[Long],
                           endTime: Option[Long],
                           executiveStatus: Status,
                           leafStatusCounts: Option[Map[Status, Int]])

  implicit val mandateStatusFormat = jsonFormat7(MandateStatus.apply)

  private[this] val results = new File("results")

  private[this] val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  private[this] val watcher = new RunListWatcher(results)

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
        Some(dir -> Source.fromFile(f).mkString.parseJson.convertTo[ReportStatus])
      } else {
        None
      }
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

  private[this] def commanderDirByIndex(runDir: File, index: Int): Directive1[File] =
    runDir.listFiles(subdirFilter(index)).headOption match {
      case Some(d) => provide(d)
      case None => reject
    }

  private[this] val MandateId = """m0(?:_\d*)*""".r
  private[this] val MultiTreeIdSegment = """[a-f0-9]{32}-\d{4}""".r

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

  private[this] def dir(file: File): Directive1[File] =
    if ( file.exists ) {
      provide(file)
    } else {
      reject
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
              (StatusCodes.NoContent, "log file does not (yet?) exist")
            }
          }
        }
      }
    }

  def leafOrBranchAttributes(multiTreeDir: File): Either[LeafReportAttributes, BranchReportAttributes] = {
    if ((multiTreeDir / "leaf.js").exists)
      Left(Source.fromFile(multiTreeDir / "leaf.js").mkString.parseJson.convertTo[LeafReportAttributes])
    else
      Right(Source.fromFile(multiTreeDir / "branch.js").mkString.parseJson.convertTo[BranchReportAttributes])
  }

  def multiTreeDirByMandateId(runDir: File, mandateId: String): Directive1[File] = {
    val tokens = mandateId.split('_').drop(1).map(_.toInt)
    // Determine the commander index from the first token
    val commanderDir = runDir.listFiles(subdirFilter(tokens.head)).head
    val commander = Source.fromFile(commanderDir / "commander.js").mkString.parseJson.convertTo[CommanderReportAttributes]

    @tailrec
    def walkTree(multiTreeDir: File, tokens: Iterable[Int]): File = tokens.headOption match {
      case None => multiTreeDir
      case Some(index) =>
        val branch = Source.fromFile(multiTreeDir / "branch.js").mkString.parseJson.convertTo[BranchReportAttributes]
        val childId = branch.children(index)
        walkTree(commanderDir / childId.toString, tokens.tail)
    }

    provide(walkTree(commanderDir / commander.root.toString, tokens.tail))
  }


  def multiTreeChildren(mandateId: String, multiTreeDir: File) =
    {
      val attrs = Source.fromFile(multiTreeDir / "branch.js").mkString.parseJson.convertTo[BranchReportAttributes]

      attrs.children.zipWithIndex map { case (cid, n) =>
        val childDir = multiTreeDir.getParentFile / cid.toString
        val status = Source.fromFile(childDir / "status.js").mkString.parseJson.convertTo[ReportStatus]
        val attrs = leafOrBranchAttributes(childDir)
        MandateStatus(
          id = mandateId + "_" + n,
          description = attrs.fold(_.name, _.name),
          composite = attrs.isRight,
          startTime = status.startTime,
          endTime = status.endTime,
          executiveStatus = status.status,
          leafStatusCounts = Some(status.leafStatusCounts)
        )
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
            extract(_ => watcher.getRunList()) { runList =>
              conditional(EntityTag(runList.checksum), DateTime(runList.timestamp)) {
                complete(runList.runs)
              }
            }
          } ~
          pathPrefix("run" / Segment ) { runId =>
            dir(results / runId) { runDir =>
              pathEndOrSingleSlash {
                complete {
                  val run = Source.fromFile(runDir / "run.js").mkString.parseJson.convertTo[RunReportAttributes]
                  val status = Source.fromFile(runDir / "status.js").mkString.parseJson.convertTo[ReportStatus]
                  val subdirs = runDir.listFiles(dirFilter).map(_.getName).sorted.toList
                  run.copy(status = Some(status), commanders = Some(subdirs))
                }
              } ~
              pathPrefix("m0") {
                path("children") {
                  complete {
                    val commanderDirs = runDir.listFiles(dirFilter)
                    commanderDirs.toList.zipWithIndex map { case (d, n) =>
                      val commander = Source.fromFile(d / "commander.js").mkString.parseJson.convertTo[CommanderReportAttributes]
                      val status = Source.fromFile(d / commander.root.toString / "status.js").mkString.parseJson.convertTo[ReportStatus]
                      MandateStatus(
                        id = s"m0_$n",
                        description = Some(commander.description),
                        composite = true,
                        startTime = status.startTime,
                        endTime = status.endTime,
                        executiveStatus = status.status,
                        leafStatusCounts = Some(status.leafStatusCounts)
                      )
                    }
                  }
                }
              } ~
              pathPrefix("m0_\\d+".r) { mandateId =>
                provide(mandateId.dropWhile(_ != '_').drop(1).toInt) { commanderIndex =>
                  commanderDirByIndex(runDir, commanderIndex) { commanderDir =>
                    path("children") {
                      complete {
                        val commander = Source.fromFile(commanderDir / "commander.js").mkString.parseJson.convertTo[CommanderReportAttributes]
                        val rootDir = commanderDir / commander.root.toString
                        multiTreeChildren(mandateId, rootDir)
                      }
                    }
                  }
                }
              } ~
              pathPrefix(MandateId) { mandateId =>
                multiTreeDirByMandateId(runDir, mandateId) { multiTreeDir =>
                  path("children") {
                    complete {
                      multiTreeChildren(mandateId, multiTreeDir)
                    }
                  } ~
                  path("status") {
                    complete {
                      val attrs = leafOrBranchAttributes(multiTreeDir)
                      val status = Source.fromFile(multiTreeDir / "status.js").mkString.parseJson.convertTo[ReportStatus]
                      MandateStatus(
                        id = mandateId,
                        description = attrs.fold(_.name, _.name),
                        composite = attrs.isRight,
                        startTime = status.startTime,
                        endTime = status.endTime,
                        executiveStatus = status.status,
                        leafStatusCounts = Some(status.leafStatusCounts)
                      )
                    }
                  } ~
                  pathPrefix("log") {
                    pathEnd {
                      getLogFileFrom(multiTreeDir, 0)
                    } ~
                    path(LongNumber) { offset =>
                      getLogFileFrom(multiTreeDir, offset)
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
