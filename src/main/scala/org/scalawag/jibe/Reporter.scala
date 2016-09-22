package org.scalawag.jibe

import java.io.{File, FileFilter}
import java.nio.file.Files

import spray.json._
import FileUtils._
import org.scalawag.jibe.backend.JsonFormat.{PersistentTarget, ShallowMandateResults}
import org.scalawag.jibe.mandate.MandateResults

import scala.io.Source
import scala.util.Try
import scala.xml.NodeSeq

object Reporter {
  private val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  private def scriptTable(label: String, dir: File, depth: Int, rowId: String): NodeSeq = {
    val exitCode = Source.fromFile(dir / "exitCode").mkString.toInt
    val script = Try(Some(Source.fromFile(dir / "script").mkString)).getOrElse(None)
    val output = Try(Source.fromFile(dir / "output").getLines()).getOrElse(Iterable.empty)

    val scriptId = s"${rowId}_s"
    val outputId = s"${rowId}_o"

    val outputElems =
      {
        if ( output.isEmpty )
          NodeSeq.Empty
        else
          <div class="row mono" shutter={outputId} shuttered="true">
            {spacers(depth + 1)}
            <div class="box transcript">
              <pre>{
                output map { l =>
                  val (spanClass, text) =
                    if ( l.startsWith("E:") )
                      ("stderr", l.substring(2))
                    else if ( l.startsWith("O:") )
                      ("stdout", l.substring(2))
                    else
                      ("stdout", l)

                  <div class={spanClass}>{text}</div>
                }
              }</pre>
            </div>
          </div>
      }

    val scriptElems =
    {
      if ( script.isEmpty || script.get.isEmpty )
        NodeSeq.Empty
      else
        <div class="row mono"  shutter={scriptId} shuttered="true">
          {spacers(depth + 1)}
          <div class="box script">
            <pre>{script.get}</pre>
          </div>
        </div>
    }

    val outputStyle = "visibility: " + ( if ( outputElems.isEmpty ) "hidden" else "inherit" )
    val scriptStyle = "visibility: " + ( if ( scriptElems.isEmpty ) "hidden" else "inherit" )

    <div class="row phase-name">
      {spacers(depth)}
      <div class="box actions" shutter-control={outputId} style={outputStyle}>Output</div>
      <div class="box right collapser" shutter-control={outputId} style={outputStyle} shutter-indicator={outputId}><i class="fa fa-caret-right"></i></div>
      <div class="box right" shutter-control={scriptId} style={scriptStyle}>Script</div>
      <div class="box right collapser" shutter-control={scriptId} style={scriptStyle} shutter-indicator={scriptId}><i class="fa fa-caret-right"></i></div>
      <div class="box description">{label} => {exitCode}</div>
    </div> ++ scriptElems ++ outputElems
  }

  private def spacers(n: Int): NodeSeq = Seq.fill(n)(<div class="box spacer">&nbsp;</div>)

  private def commandTable(dir: File, depth: Int, rowId: String): NodeSeq = {
    val possibleDirsInOrder = List(
      "test" -> "Check",
      "test/length_check" -> "Length Check",
      "test/content_check" -> "Content Check",
      "perform" -> "Execute"
    )

    possibleDirsInOrder.zipWithIndex flatMap { case ((dirName, label), n) =>
      val d = dir / dirName
      // exitCode will always be present in a directory that represents script output.  Use that as an indicator.
      if ( ( d / "exitCode" ).exists )
        scriptTable(label, d, depth, s"${rowId}_${n}")
      else NodeSeq.Empty
    }
  }

  def mandate(dir: File, depth: Int, rowId: String, description: Option[String] = None, icon: Option[String] = None): NodeSeq = {
    val mr = Source.fromFile(dir / "mandate.js").mkString.parseJson.convertTo[ShallowMandateResults]

    val (outcomeClass, outcomeIcon) = mr.outcome match {
      case MandateResults.Outcome.SUCCESS => ("success", "fa fa-check")
      case MandateResults.Outcome.FAILURE => ("failure", "fa fa-exclamation")
      case MandateResults.Outcome.USELESS => ("skipped", "fa fa-times")
    }

    val innards =
      if ( mr.composite ) {
        dir.listFiles(dirFilter).zipWithIndex.flatMap { case (m, n) =>
          mandate(m, depth + 1, s"${rowId}_${n}")
        }.toSeq
      } else {
        commandTable(dir, depth + 1, rowId)
      }

    val iconClass = icon.map( i => s"fa $i" ).getOrElse(outcomeIcon)

    <div class={s"mandate $outcomeClass"}>
      <div class="row summary">
        {spacers(depth)}
        <div class="box collapser" shutter-control={rowId} shutter-indicator={rowId}><i class="fa fa-caret-right"></i></div>
        <div class="box icon" shutter-control={rowId}><i class={iconClass}></i></div>
<!--      <div class="box outcome"><div style="height: 1em; width: 20em; background: linear-gradient(to right, green 60%, yellow 60%);"></div></div> -->
        <div class="box time" shutter-control={rowId}>{mr.elapsedTime} ms</div>
        <div class="box description" shutter-control={rowId}>{description.getOrElse(mr.description.getOrElse(""))}&nbsp;</div>
      </div>
      <div shutter={rowId} shuttered="true">
        {innards}
      </div>
    </div>
  }

  def targets(dir: File): NodeSeq = {
    dir.listFiles(dirFilter).zipWithIndex flatMap { case (d, n)  =>
      val rowId = s"r$n"

      val t = Source.fromFile(d / "target.js").mkString.parseJson.convertTo[PersistentTarget]
      val exceptionFile = d / "exception"
        if ( exceptionFile.exists ) {
          <div class="mandate failure">
            <div class="row summary">
              <div class="box collapser" shutter-control={rowId} shutter-indicator={rowId}><i class="fa fa-caret-right"></i></div>
              <div class="box icon" shutter-control={rowId}><i class="fa fa-dot-circle-o"></i></div>
              <div class="box description" shutter-control={rowId}>{t.username}@{t.hostname}:{t.port} ({t.commander})</div>
            </div>
            <div shutter={rowId} shuttered="true" class="row mono">
              <div class="box spacer">&nbsp;</div>
              <div class="box stack-trace">
                <pre>{ Source.fromFile(exceptionFile).mkString }</pre>
              </div>
            </div>
          </div>
        } else {
          mandate(d, 0, s"${rowId}_0", Some(s"${t.username}@${t.hostname}:${t.port} (${t.commander})"), Some("fa-dot-circle-o"))
        }
    } toSeq
  }

  def generate(input: File, output: File, symlink: File ): Unit = {

    FileUtils.writeFileWithPrintWriter(output) { pw =>
      pw.println(
        <html>
          <head>
            <link rel="stylesheet" href="http://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.6.3/css/font-awesome.min.css"/>
            <script src="http://ajax.googleapis.com/ajax/libs/jquery/1.8.1/jquery.min.js"></script>
            <style>
              html {{
                font-family: sans-serif;
              }}

              body {{
                min-width: 1000px;
              }}

              div.success div.summary {{
                background-color: #DFF2BF;
              }}

              div.failure div.summary {{
                background-color: #FFBABA;
              }}

              div.running div.summary {{
                background-color: #c3e6fc;
              }}

              div.skipped div.summary {{
                background-color: #FEEFB3;
              }}

              div.waiting div.summary {{
              }}

              div.row {{
                width: 100%;
                clear: both;
              }}

              div.box {{
                //border: solid black 1pt;
              padding-top: .5em;
              padding-bottom: .5em;
              }}

              div.collapser {{
                width: 1.5em;
                float: left;
                text-align: center;
              }}

              div.phase-name {{
                color: white;
                background-color: gray;
              }}

              div.icon {{
                width: 1.5em;
                float: left;
                text-align: center;
              }}

              div.spacer {{
                width: 1.5em;
                float: left;
                background-color: white;
              }}

              div.time {{
                float: right;
                text-align: right;
                padding-right: 1em;
              }}

              div.outcome {{
                float: right;
                width: 20em;
                padding-left: .5em;
                padding-right: .5em;
              }}

              div.description {{
                padding-left: .5em;
                overflow: auto;
              }}

              div.actions {{
                float: right;
                padding-right: 1em;
              }}

              div.right {{
                float: right;
              }}

              pre {{
                margin: 0;
                padding: .5em;
                line-height: 1.3em;
                border: 1px solid #cccccc;
                border-radius: .5em;
                overflow: auto;
              }}

              div.transcript pre {{
                background-color: black;
              }}

              div.script {{
//                padding-bottom: 0;
              }}

              div.script pre {{
                background-color: #eeeeee;
              }}

              div.stack-trace pre {{
                background-color: #ffdddd;
              }}

              div.mono {{
                padding-top: 0;
              }}

              div.stdout {{
                color: lightgray;
              }}

              div.stderr {{
                color: #ee6666;
              }}

              a i {{
                text-decoration: none !important;
                color: inherit;
              }}

              @keyframes spinup {{
                from {{ transform: rotate(90deg); }}
                to {{ transform: rotate(0deg); }}
              }}

              @keyframes spindown {{
                from {{ transform: rotate(0deg); }}
                to {{ transform: rotate(90deg); }}
              }}

              div.spinup {{
                animation: spinup 200ms;
              }}

              div.spindown {{
                animation: spindown 200ms;
                animation-fill-mode: forwards;
              }}

              div.actions i {{
                cursor: pointer;
              }}

              div.actions i.toggle-on {{
                color: black;
              }}

              div.actions i.toggle-off {{
                color: #dddddd;
              }}

              div.mandate.hidden {{
                display: none;
              }}

              div.summary {{
                margin-top: 1px;
              }}
            </style>
            <script type="text/javascript" src="../code.js"></script>
          </head>
          <body>
            <div class="row">
              <div class="box actions">
                <i class="fa fa-angle-double-down" onclick="shutterOpenAll()" title="Expand All"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-angle-down" onclick="shutterOpenMandates()" title="Expand Mandates"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-angle-double-up" onclick="shutterCloseAll()" title="Collapse All"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-exclamation toggle-on" outcome="failure" onclick="toggleHide(this)" title="Hide Failed"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-times toggle-on" outcome="skipped" onclick="toggleHide(this)" title="Hide Skipped"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-check toggle-on" outcome="success" onclick="toggleHide(this)" title="Hide Successful"></i>
              </div>
              <div class="box description">{ input.getParentFile.getName }</div>
            </div>

            { targets(input) }
          </body>
        </html>
      )
    }

    try {
      val symlinkPath = symlink.toPath
      Files.deleteIfExists(symlinkPath)
      Files.createSymbolicLink(symlinkPath, symlinkPath.getParent.relativize( output.toPath ))
    } catch {
      case uoe: UnsupportedOperationException => println("Your OS sucks. Got symlinks?")
      case unknown: Exception => println("Failed to Create symlink: " + unknown)
    }

  }
}
