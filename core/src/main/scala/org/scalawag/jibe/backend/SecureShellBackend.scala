package org.scalawag.jibe.backend

import java.io._
import java.util.concurrent.TimeoutException

import MandateExecutionLogging._
import org.scalawag.jibe.mandate.command.FileContent
import org.scalawag.timber.api.Logger

import scala.concurrent.duration.Duration
import scala.io.Source

class SecureShellBackend(ssh: SshInfo, sudo: Boolean = false, commandTimeout: Duration = Duration.Inf) {

  // Command can include bash scripting with ';' and '&&' and can use environment variables

  def exec(log: Logger, command: String): Int =
    execInternal(
      log,
      if ( sudo )
        s"""sudo /bin/bash <<'EOS'
           |$command
           |EOS""".stripMargin
      else
        command,
      null
    )

  def execResource(log: Logger, scriptPath: String, prepend: Iterable[String]): Int = {
    val scriptResource = Option(this.getClass.getResourceAsStream(scriptPath)) getOrElse {
      throw new RuntimeException(s"unable to load script resource from classpath: $scriptPath")
    }
    val scriptLines = Source.fromInputStream(scriptResource).getLines

    val fullScript =
      if ( sudo )
        Iterable("sudo /bin/bash <<'EOS'") ++ prepend ++ scriptLines ++ Iterable("EOS")
      else
        prepend ++ scriptLines

    execInternal(
      log,
      fullScript.mkString(endl),
      null
    )
  }

  def scp(log: Logger, source: FileContent, destination: File, mode: String = "0644"): Int = {
    import scala.collection.JavaConversions._
    execInternal(
      log,
      s"${ if ( sudo ) "/usr/bin/sudo " else "" }/usr/bin/scp -t $destination",
      new SequenceInputStream(Iterator(
        new ByteArrayInputStream(s"C$mode ${source.length} ${destination.getName}\n".getBytes),
        source.openInputStream,
        new ByteArrayInputStream(Array(0.toByte))
      ))
    )
  }

  private[this] def execInternal(log: Logger, command: String, stdin: InputStream): Int = {
    // Log here so that it appears even when something prevents the connection.
    log.debug(CommandContent)(command)

    Sessions.withChannel(ssh) { c =>
      c.setInputStream(stdin)

      // JSch will close these for us.
      c.setOutputStream(new OutputStreamToLogger(log.info(CommandOutput)(_)))
      c.setErrStream(new OutputStreamToLogger(log.error(CommandOutput)(_)))

      c.setCommand(command)
      c.connect()

      val expiryTime =
        if ( commandTimeout.isFinite )
          System.currentTimeMillis + commandTimeout.toMillis
        else
          Long.MaxValue

      while ( !c.isClosed && System.currentTimeMillis < expiryTime )
        Thread.sleep(100)

      while ( !c.isClosed )
        if ( System.currentTimeMillis < expiryTime )
          Thread.sleep(100)
        else
          throw new TimeoutException("command took too long to complete")

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
