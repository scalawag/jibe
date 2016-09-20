package org.scalawag.jibe

import java.io.{File, FileFilter}

import spray.json._
import FileUtils._
import org.scalawag.jibe.backend.JsonFormat.{PersistentTarget, ShallowMandateResults}
import org.scalawag.jibe.mandate.MandateResults

import scala.io.Source
import scala.util.Try
import scala.xml.{Elem, NodeSeq}

object Reporter {
  private val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  private def scriptTable(label: String, dir: File): NodeSeq = {
    val exitCode = Source.fromFile(dir / "exitCode").mkString.toInt
    val script = Try(Source.fromFile(dir / "script").getLines()).getOrElse(Iterator.empty)
    val output = Source.fromFile(dir / "output").getLines()

    val outputElems =
      {
        if ( output.isEmpty )
          NodeSeq.Empty
        else
          <tr>
            <td bgcolor="black">
              <pre>{
                output map { l =>
                  val (color, text) =
                    if ( l.startsWith("E:") )
                      ("red", l.substring(2))
                    else if ( l.startsWith("O:") )
                      ("lightgray", l.substring(2))
                    else
                      ("lightgray", l)

                  <div><font color={color}>{text}</font></div>
                }
              }</pre>
            </td>
          </tr>
      }

    val scriptElems =
    {
      if ( script.isEmpty )
        NodeSeq.Empty
      else
        <tr>
          <td>
            <pre>{
  script map { l => <div>{l}</div> }
}</pre>
          </td>
        </tr>
    }

    Seq(
      <tr>
        <td colspan="2" bgcolor="lightgray">{label} => {exitCode}</td>
      </tr>,
      scriptElems,
      outputElems).flatten

  }

  private def commandTable(dir: File): NodeSeq = {
    val possibleDirsInOrder = List("test", "test/length_check", "test/content_check", "perform")

    possibleDirsInOrder.flatMap { n =>
      val d = dir / n
      // exitCode will always be present in a directory that represents script output.  Use that as an indicator.
      if ( ( d / "exitCode" ).exists )
        scriptTable(n, d)
      else NodeSeq.Empty
    }
  }

  def mandate(dir: File): NodeSeq = {
    val mr = Source.fromFile(dir / "results.js").mkString.parseJson.convertTo[ShallowMandateResults]

    val outcomeColor = mr.outcome match {
      case MandateResults.Outcome.SUCCESS => "green"
      case MandateResults.Outcome.FAILURE => "red"
      case MandateResults.Outcome.USELESS => "yellow"
//      case MandateResults.Outcome.SUCCESS => ("#4F8A10","#DFF2BF")
//      case MandateResults.Outcome.FAILURE => ("#D8000C","#FFBABA")
//      case MandateResults.Outcome.USELESS => ("#9F6000","#FEEFB3")
    }

    val header =
      <tr bgcolor={outcomeColor}>
        <td>+<!-- &#x2795; &#x2796; --></td>
        <td>{mr.description.getOrElse("")}</td>
        <td>{mr.elapsedTime} ms</td>
        <td>{mr.outcome}</td>
      </tr>

    val innards =
      <td colspan="4">
        <table border="1" style="margin-left: 2em">
          {
          if ( mr.composite ) {
            dir.listFiles(dirFilter).flatMap(mandate).toSeq
          } else {
            commandTable(dir)
          }
          }
        </table>
      </td>

    Seq(header,innards)
  }

  def targets(dir: File): NodeSeq = {
    dir.listFiles(dirFilter) flatMap { d =>
      val t = Source.fromFile(d / "target.js").mkString.parseJson.convertTo[PersistentTarget]
      val exceptionFile = d / "exception"
      val innards: Elem =
        if ( exceptionFile.exists ) {
          <pre>{ Source.fromFile(exceptionFile).mkString }</pre>
        } else {
          <table border="1" style="margin-left: 2em">
            { mandate(d) }
          </table>
        }

      <tr>
        <td>{t.username}@{t.hostname}:{t.port} ({t.commander})</td>
      </tr>
      <tr>
        <td>
          {innards}
        </td>
      </tr>
    } toSeq
  }

  def generate(input: File, output: File): Unit = {

    FileUtils.writeFileWithPrintWriter(output) { pw =>
      pw.println(
        <html>
          <body>
            <table border="1">
              { targets(input) }
            </table>
          </body>
        </html>
      )
    }
  }
}
