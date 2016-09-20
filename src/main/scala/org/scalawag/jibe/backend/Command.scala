package org.scalawag.jibe.backend

import java.io._

import org.scalawag.jibe.FileUtils._

import scala.io.Source

trait Command {
  def test(target: Target, dir: File): Int
  def perform(target: Target, dir: File): Int

  // Command can include bash scripting with ';' and '&&' and can use environment variables

  def ssh(target: Target, command: String, reportDir: File): Int =
    exec(
      target,
      if ( target.sudo )
        s"""sudo /bin/bash <<'EOS'
           |$command
           |EOS""".stripMargin
      else
        command,
     null,
     reportDir
    )

  def sshResource(target: Target, script: String, context: Map[String, Any], reportDir: File): Int = {
    val scriptResource = Option(this.getClass.getResourceAsStream(script)) getOrElse {
      throw new RuntimeException(s"unable to load script resource from classpath: $script")
    }
    val scriptLines = Source.fromInputStream(scriptResource).getLines

    val contextLines = context map { case (k,v) => s"""$k="$v"""" }

    val fullScript =
      if ( target.sudo )
        Iterable("sudo /bin/bash <<'EOS'") ++ contextLines ++ scriptLines ++ Iterable("EOS")
      else
        contextLines ++ scriptLines

    exec(
      target,
      fullScript.mkString(endl),
      null,
      reportDir
    )
  }

  def scp(target: Target, source: File, destination: File, reportDir: File, mode: String = "0644"): Int = {
    import scala.collection.JavaConversions._
    exec(
      target,
      s"${ if ( target.sudo ) "/usr/bin/sudo " else "" }/usr/bin/scp -t $destination",
      new SequenceInputStream(Iterator(
        new ByteArrayInputStream(s"C$mode ${source.length} ${source.getName}\n".getBytes),
        new FileInputStream(source),
        new ByteArrayInputStream(Array(0.toByte))
      )),
      reportDir
    )
  }

  private[this] def exec(target: Target, command: String, stdin: InputStream, reportDir: File): Int =
    Sessions.withChannel(target) { c =>
      c.setInputStream(stdin)

      writeFileWithOutputStream(reportDir / "output") { out =>
        val demux = new DemuxOutputStream(out)

        // JSch will close these for us.
        c.setOutputStream(demux.createChannel("O:"))
        c.setErrStream(demux.createChannel("E:"))

        writeFileWithPrintWriter(reportDir / "script") { w =>
          w.print(command)
        }

        c.setCommand(command)
        c.connect()

        // TODO: maybe not block here and use futures instead.
        while ( !c.isClosed )
          Thread.sleep(100)

        c.disconnect()

        writeFileWithPrintWriter(reportDir / "exitCode") { ec =>
          ec.print(c.getExitStatus)
        }
      }

      c.getExitStatus
    }

  private[this] val endl = System.getProperty("line.separator")

  /** This is a class that acts kind of like a console.  We use it to create two OutputStreams (one for stdout and
    * one for stderr) and it keeps track of which stream is sending which bytes.  It makes sure that the lines are
    * each from a single stream and tags each line with a prefix so that we can tell which stream it came from.
    * This way, we can see the stdout and stderr streams interleaved approximately how that came out of the subprocess
    * instead of seeing them separately.
    *
    * @param demux the OutputStream that will receive all of the tagged bytes.
    */

  class DemuxOutputStream(demux: OutputStream) {
    private[this] var channels: List[Channel] = Nil

    private[this] val lock = new Object

    private[this] class Channel(tag: String) extends OutputStream {
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
        if ( ! buffer.isEmpty ) {
          lock.synchronized {
            demux.write(tag.getBytes)
            demux.write(buffer.toArray)
            demux.write(endl.getBytes)
          }
          buffer = Seq.empty
        }
    }

    def createChannel(tag: String): OutputStream = new Channel(tag)

    def close(): Unit = {
      channels.foreach { c =>
        c.close()
        c.flushBuffer()
      }
    }
  }
}
