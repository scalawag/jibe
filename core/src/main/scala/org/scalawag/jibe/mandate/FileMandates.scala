package org.scalawag.jibe.mandate

import java.io._

import org.fusesource.scalate._
import org.scalawag.jibe.mandate.command._
import org.scalawag.jibe.multitree._

abstract class WriteRemoteFileBase(val remotePath: File) extends Mandate with MandateHelpers {
  protected def doesRemoteFileAlreadyContain(content: FileContent)(implicit context: MandateExecutionContext): Boolean = {
    import context._

    // Do a quick check that it exists and is the right length before proceeding.

    if (runCommand(IsRemoteFileLength(remotePath, content.length))) {
      log.debug("remote file exists and has the correct length, calculating checksum")

      val answer = runCommand(IsRemoteFileMD5(remotePath, content.md5))
      log.debug(s"checksum is ${ if ( answer ) "" else "not " }correct")
      answer

    } else {
      log.debug("remote file has a different length than expected")
      false
    }
  }

  protected def writeRemoteFile(content: FileContent)(implicit context: MandateExecutionContext): Unit = {
    runCommand(command.WriteRemoteFile(remotePath, content))
  }
}

object WriteRemoteFileBase {
  def defaultDecorationsFor(remotePath: File) =
    Set[MultiTreeDecoration](Consequences(FileResource(remotePath.getAbsolutePath)))
}

object WriteRemoteFile {
  case class WriteRemoteFile(override val remotePath: File, content: FileContent)
    extends WriteRemoteFileBase(remotePath) with StatelessMandate
  {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      doesRemoteFileAlreadyContain(content)

    override def takeAction(implicit context: MandateExecutionContext) =
      writeRemoteFile(content)
  }

  def apply(remotePath: File, content: FileContent) = MultiTreeLeaf(
    mandate = new WriteRemoteFile(remotePath, content),
    name = Some(s"write remote file: $content -> $remotePath (${content.length} bytes)"),
    decorations = WriteRemoteFileBase.defaultDecorationsFor(remotePath)
  )
}

object WriteRemoteFileFromTemplate {
  case class WriteRemoteFileFromTemplate(override val remotePath: File, template: FileContent, values: Map[String, Any])
    extends WriteRemoteFileBase(remotePath) with StatefulMandate[FileContent]
  {
    override def createState(implicit context: MandateExecutionContext) = {
      context.log.debug(s"generating content from template $template and values $values")
      // Anything less than 16k or already in memory will be expanded in memory, anything larger than that and we'll
      // write a temp file.
      template match {
        case FileContentFromFile(f) =>
          context.log.debug(s"full path to template file is ${f.getAbsoluteFile}")

          // Only use a temp file
          if ( f.length > 16384 ) {
            val tf = File.createTempFile("jibe_",".tmp")
            context.log.debug(s"expanding template to a temporary file: $tf")

            val engine = new TemplateEngine
            val tmpl = engine.load(TemplateSource.fromFile(f))

            val pw = new PrintWriter(new FileWriter(tf))
            val ctxt = new DefaultRenderContext("testing", engine, pw)

            values foreach { case (key, value) =>
              ctxt.attributes(key) = value
            }

            tmpl.render(ctxt)
            pw.close()

            FileContent(tf)
          } else {
            val engine = new TemplateEngine
            val output = engine.layout(TemplateSource.fromFile(f), values) // TODO: charset?

            context.log.debug(s"content is this:\n$output")

            FileContent(output.getBytes) // TODO: charset?
          }

        case FileContentFromArray(a) =>

          val engine = new TemplateEngine
          val output = engine.layout(TemplateSource.fromText("internal.ssp", new String(a.toArray)), values) // TODO: charset?

          context.log.debug(s"content is this:\n$output")

          FileContent(output.getBytes) // TODO: charset?
      }
    }

    override def isActionCompleted(state: FileContent)(implicit context: MandateExecutionContext) =
      doesRemoteFileAlreadyContain(state)

    override def takeAction(state: FileContent)(implicit context: MandateExecutionContext) =
      writeRemoteFile(state)

    private[this] def cleanup(state: FileContent)(implicit context: MandateExecutionContext) =
      state match {
        case FileContentFromFile(f) =>
          context.log.debug(s"deleting temporary file $f")
          f.delete()
        case FileContentFromArray(_) =>
        // noop
      }
  }

  def apply(remotePath: File, template: FileContent, values: Map[String, Any]) = MultiTreeLeaf(
    mandate = new WriteRemoteFileFromTemplate(remotePath, template, values),
    name = Some(s"write remote file with template: $template -> $remotePath"),
    decorations = WriteRemoteFileBase.defaultDecorationsFor(remotePath)
  )
}
