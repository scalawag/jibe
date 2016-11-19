package org.scalawag.jibe.report.cli

import java.io.{File, PrintWriter}

import org.scalawag.druthers._
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.multitree.MultiTreeId
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report.JsonFormats._
import org.scalawag.jibe.report.cli.TextReport.TextReportOptions
import org.scalawag.timber.api.{Dispatcher, Entry}
import org.scalawag.timber.backend.DefaultDispatcher
import spray.json._

import scala.io.Source

class TextReport(pw: PrintWriter, dir: File, options: TextReportOptions) {
  private[this] case class MultiTreeInfo(id: MultiTreeId,
                                         pathCount: Int,
                                         reportDir: File,
                                         description: String,
                                         status: ReportStatus,
                                         children: List[MultiTreeInfo])

  private[this] val runDir =
    if ( options.recent ) {
      dir.listFiles.filter(_.isDirectory).sorted.lastOption getOrElse {
        throw new IllegalArgumentException("no runs in the specified directory")
      }
    } else {
      dir
    }

  private[this] def loadStatus(f: File) = Source.fromFile(f).mkString.parseJson.convertTo[ReportStatus]

  private[this] def indent(depth: Int) = "  " * depth

  private[this] def statusToColor(s: Status) = s match {
    case FAILURE  => Console.RED
    case BLOCKED  => Console.MAGENTA
    case SUCCESS  => Console.GREEN
    case NEEDED   => Console.GREEN
    case UNNEEDED => Console.YELLOW
    case SKIPPED  => Console.YELLOW
    case PENDING  => Console.WHITE
    case RUNNING  => Console.CYAN
  }

  private[this] def styled(style: String, text: String) =
    if ( options.color )
      style + text + Console.RESET
    else
      text

  private[this] object multiTreeInfoCache {
    private[this] var cache = Map.empty[(String, MultiTreeId), MultiTreeInfo]

    def load(commander: String, id: MultiTreeId): MultiTreeInfo = cache.get(commander, id) getOrElse {
      val dir = runDir / commander / id.toString
      val status = loadStatus(dir / "status.js")

      val (pathCount, description, children) =
        if ( ( dir / "leaf.js" ).exists ) {
          val leaf = Source.fromFile(dir / "leaf.js").mkString.parseJson.convertTo[LeafReportAttributes]
          (leaf.pathCount, leaf.name.getOrElse(leaf.stringRepresentation), Nil)
        } else {
          val branch = Source.fromFile(dir / "branch.js").mkString.parseJson.convertTo[BranchReportAttributes]
          (branch.pathCount, branch.name.getOrElse(""), branch.children.map(load(commander, _)))
        }

      val info = MultiTreeInfo(id, pathCount, dir, description, status, children)

      cache += (commander, id) -> info

      info
    }
  }

  private[this] def writeMultiTreeSummary(description: String, info: ReportStatus, depth: Int) = {
    val duration = for { end <- info.endTime ; start <- info.startTime } yield { s" (${end - start} ms)" }
    pw.println(indent(depth) + styled(statusToColor(info.status), s"+ $description -> ${info.status}${duration.getOrElse("")}"))
  }

  private[this] def writeMultiTreeSummaries(info: MultiTreeInfo, depth: Int): Unit = {
    writeMultiTreeSummary(s"[${info.id.fingerprint.take(6)}-${info.id.serial}] (${info.pathCount}) ${info.description}", info.status, depth)
    info.children.foreach(writeMultiTreeSummaries(_, depth + 1))
    if ( options.logs || ( options.errorLogs && ( info.status.status == BLOCKED || info.status.status == FAILURE ) ) ) {
      val log = info.reportDir / "log"
      if (log.exists)
        writeLog(log, depth + 2)
    }
  }

  // Stuff to parse the raw log into structured data that's easier to format

  private trait TopLevelElement
  private case class LogLine(tag: String, level: String, timestamp: String, text: String) extends TopLevelElement
  private case class Command(name: LogLine, content: Seq[LogLine] = Seq.empty, output: Seq[LogLine] = Seq.empty, exitCode: Option[LogLine] = None) extends TopLevelElement
  private case class StackTrace(message: Seq[LogLine], location: Seq[LogLine] = Seq.empty) extends TopLevelElement
  private case class ThrownException(traces: Seq[StackTrace]) extends TopLevelElement
  private case class FunctionCall(name: String) extends TopLevelElement
  private case class FunctionExit(answer: String) extends TopLevelElement

  private def parseLogLine(s: String) = {
    val Array(tag, level, timestamp, text) = s.split("\\|", 4)
    LogLine(tag, level, timestamp, text)
  }

  private[this] def writeLog(log: File, initialDepth: Int = 0): Unit = {
    var depth = initialDepth

    Source.fromFile(log).getLines.map(parseLogLine) foreach { line =>
      val out =
        line.tag match {
          case "EE" =>
            Some(Console.RED, line.text, 0)

          case "CS" =>
            Some(Console.CYAN + Console.UNDERLINED, "Command: " + line.text, 1)

          case "CE" =>
            Some(Console.CYAN + Console.UNDERLINED, "Returned: " + line.text, -1)

          case "CC" =>
            None // Don't output script content in this mode

          case "FS" =>
            Some(Console.BLUE + Console.UNDERLINED, "Function: " + line.text, 1)

          case "FR" =>
            Some(Console.BLUE + Console.UNDERLINED, "Returned: " + line.text, -1)

          case _ => // handles normal log output and command stdout/stderr
            line.level match {
              case "DBG" =>
                if ( options.debug )
                  Some(Console.MAGENTA, line.text, 0)
                else
                  None
              case "INF" =>
                Some(Console.WHITE, line.text, 0)
              case "WRN" =>
                Some(Console.YELLOW, line.text, 0)
              case "ERR" =>
                Some(Console.RED, line.text, 0)
            }

        }

      out match {
        case Some((style, text, shift)) =>
          if ( shift < 0 )
            depth += shift

          pw.println(indent(depth) + styled(style, text))

          if ( shift > 0 )
            depth += shift
        case None =>
          // NOOP
      }
    }
  }

  def renderReport(): Unit = {
    val runStatus = loadStatus(runDir / "status.js")
    val runAttrs = Source.fromFile(runDir / "run.js").mkString.parseJson.convertTo[RunReportAttributes]

    writeMultiTreeSummary(runDir.getName, runStatus, 0)

    runDir.listFiles().toList.filter(_.isDirectory).map(_.getName) foreach { cid =>
      val commanderDir = runDir / cid
      val attrs = Source.fromFile(commanderDir / "commander.js").mkString.parseJson.convertTo[CommanderReportAttributes]

      val root = multiTreeInfoCache.load(cid, attrs.root)
      writeMultiTreeSummary(attrs.description, root.status, 1)
      writeMultiTreeSummaries(root, 2)
    }

    pw.println()

    pw.println("Summary: " + styled(statusToColor(runStatus.status), runStatus.status.toString))
    Status.values foreach { s =>
      runStatus.leafStatusCounts.get(s) foreach { n =>
        pw.println("  " + styled(statusToColor(s), s"$s: $n"))
      }
    }
  }

}

object TextReport {
  // Silence logging entirely
  DefaultDispatcher.set(new Dispatcher {
    override def dispatch(entry: Entry) = {}
  })

  case class TextReportOptions(@Usage("use terminal escapes to colorize output")
                               color: Boolean = true,
                               @Usage("show the most recent run in the specified results directory")
                               recent: Boolean = false,
                               @Usage("show debug-level logging")
                               debug: Boolean = false,
                               @Usage("show all mandate logs")
                               logs: Boolean = false,
                               @Usage("show mandate logs from failed mandates")
                               errorLogs: Boolean = false)

  def main(args: Array[String]): Unit = {
    val parser = new OptionsParser[TextReportOptions](ParserConfiguration.withShortKeys)

    def usageError(error: Option[UsageException] = None): Unit = {
      error.foreach(_.errors.foreach( e => System.err.println(s"ERROR: $e")))
      System.err.println("Usage: TextReport [options] <dir>")
      parser.usage().foreach(System.err.println)
      System.exit(1)
    }

      try {
        val (options, bareWords) = parser.parse(args)

        if ( bareWords.length != 1 )
          usageError()

        val pw = new PrintWriter(System.out)
        new TextReport(pw, bareWords.head, options).renderReport()
        pw.flush()

      } catch {
        case e: UsageException => usageError(Some(e))
      }
  }
}