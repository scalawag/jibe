package org.scalawag.jibe

import java.io.{File, FileFilter}

import org.scalawag.jibe.backend.{MandateResults, ShallowMandateResults}
import spray.json._
import org.scalawag.jibe.backend.ShallowMandateResults.JSON._
import FileUtils._

import scala.io.Source
import scala.xml.{Elem, NodeSeq}

object Reporter {
  private val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  private def scriptTable(label: String, dir: File): NodeSeq = {
    val exitCode = Source.fromFile(dir / "exitCode").mkString.toInt
    val stdout = Source.fromFile(dir / "stdout").getLines()
    val stderr = Source.fromFile(dir / "stderr").getLines()

    val o =
      {
        if ( stdout.isEmpty )
          NodeSeq.Empty
        else
          <tr>
            <td>stdout</td>
            <td>
              {
                stdout.map( l => <div>{l}</div>)
              }
            </td>
          </tr>
      }

    val e =
    {
      if ( stderr.isEmpty )
        NodeSeq.Empty
      else
        <tr>
          <td>stderr</td>
          <td>
            {
            stderr.map( l => <div>{l}</div>)
            }
          </td>
        </tr>
    }

    Seq(
      <tr>
        <td colspan="2">{label}</td>
      </tr>
      <tr>
        <td>exit code</td>
        <td>{exitCode}</td>
      </tr>,
      o,e).flatten

  }

  private def commandTable(dir: File): NodeSeq = {
    val testDir = dir / "test"
    val performDir = dir / "perform"

    Seq(scriptTable("test", testDir), if ( performDir.exists() ) scriptTable("perform", performDir) else NodeSeq.Empty).flatten
  }

  def generate(input: File, output: File): Unit = {

    FileUtils.writeFileWithPrintWriter(output) { pw =>

      def mandate(dir: File): NodeSeq = {
        val mr = Source.fromFile(dir / "results.js").mkString.parseJson.convertTo[ShallowMandateResults]

        val outcomeColor = mr.outcome match {
          case MandateResults.Outcome.SUCCESS => "green"
          case MandateResults.Outcome.FAILURE => "red"
          case MandateResults.Outcome.USELESS => "yellow"
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

      pw.println(
        <html>
          <body>
            <table border="1">
              { mandate(input) }
            </table>
          </body>
        </html>
      )
    }
  }
}
