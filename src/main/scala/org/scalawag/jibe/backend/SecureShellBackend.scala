package org.scalawag.jibe.backend

import java.io._

import org.scalawag.jibe.mandate.MandateExecutionContext
import MandateExecutionLogging._
import org.scalawag.jibe.mandate.command.FileContent

import scala.io.Source

class SecureShellBackend(ssh: SshInfo, sudo: Boolean = false) {

  // Command can include bash scripting with ';' and '&&' and can use environment variables

  def exec(mcontext: MandateExecutionContext, command: String): Int =
    execInternal(
      mcontext,
      if ( sudo )
        s"""sudo /bin/bash <<'EOS'
           |$command
           |EOS""".stripMargin
      else
        command,
      null
    )

  def execResource(mcontext: MandateExecutionContext, scriptPath: String, context: Map[String, Any]): Int = {
    val scriptResource = Option(this.getClass.getResourceAsStream(scriptPath)) getOrElse {
      throw new RuntimeException(s"unable to load script resource from classpath: $scriptPath")
    }
    val scriptLines = Source.fromInputStream(scriptResource).getLines

    val contextLines = context map { case (k,v) => s"""$k="$v"""" }

    val fullScript =
      if ( sudo )
        Iterable("sudo /bin/bash <<'EOS'") ++ contextLines ++ scriptLines ++ Iterable("EOS")
      else
        contextLines ++ scriptLines

    execInternal(
      mcontext,
      fullScript.mkString(endl),
      null
    )
  }

  def scp(mcontext: MandateExecutionContext, source: FileContent, destination: File, mode: String = "0644"): Int = {
    import scala.collection.JavaConversions._
    execInternal(
      mcontext,
      s"${ if ( sudo ) "/usr/bin/sudo " else "" }/usr/bin/scp -t $destination",
      new SequenceInputStream(Iterator(
        new ByteArrayInputStream(s"C$mode ${source.length} ${destination.getName}\n".getBytes),
        source.openInputStream,
        new ByteArrayInputStream(Array(0.toByte))
      ))
    )
  }

  private[this] def execInternal(mcontext: MandateExecutionContext, command: String, stdin: InputStream): Int = {
    // Log here so that it appears even when something prevents the connection.
    mcontext.log.debug(CommandContent)(command)

    Sessions.withChannel(ssh) { c =>
      c.setInputStream(stdin)

      // JSch will close these for us.
      c.setOutputStream(new OutputStreamToLogger(mcontext.log.info(CommandOutput)(_)))
      c.setErrStream(new OutputStreamToLogger(mcontext.log.error(CommandOutput)(_)))

      c.setCommand(command)
      c.connect()

      // TODO: maybe not block here and use futures instead.
      val expiry = System.currentTimeMillis + 10000
      while ( !c.isClosed && System.currentTimeMillis < expiry )
        Thread.sleep(100)

      c.disconnect()

      c.getExitStatus
    }
  }

  private[this] val endl = System.getProperty("line.separator")

  private[this] class OutputStreamToLogger(flushFn: String => Unit) extends OutputStream {
    private[this] var buffer: Seq[Byte] = Seq.empty

    // TODO: This class could be quite a bit more efficient if we handle all of the OutputStream methods instead of
    // TODO: just the bare minimum.  Right now, everything will get written byte-by-byte.  I don't know how much that
    // TODO: matters, since we're reading stuff across a network, it's probably pretty slow anyway.

    override def write(b: Int) = {
      val s = new String(Array(b.toByte))
      if ( s == endl )
        flushBuffer()
      else
        buffer :+= b.toByte
    }

    def flushBuffer() =
      if (!buffer.isEmpty) {
        flushFn(new String(buffer.toArray)) // TODO: charset issues
        buffer = Seq.empty
      }

    override def close() = {
      flushBuffer()
    }
  }
}
