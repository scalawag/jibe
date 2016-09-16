package org.scalawag.jibe.backend

import java.io._

import com.jcraft.jsch.{ChannelExec, JSch, UserInfo}
import org.scalawag.jibe.FileUtils._

class Session(val s: com.jcraft.jsch.Session) {
  // Command can include bash scripting with ';' and '&&' and can use environment variables

  def execute(command: String, reportDir: File, sudo: Boolean = false): Int = {
    val c = s.openChannel("exec").asInstanceOf[ChannelExec]
    c.setInputStream(null)

    writeFileWithOutputStream(reportDir / "output") { out =>
      val demux = new DemuxOutputStream(out)
      c.setOutputStream(demux.createChannel("O:"))
      c.setErrStream(demux.createChannel("E:"))

      val actualCommand =
        if ( sudo )
          s"""sudo /bin/bash <<'EOS'
              |$command
              |EOS
         """.stripMargin
        else
          command

      writeFileWithPrintWriter(reportDir / "script") { w =>
        w.print(actualCommand)
      }

      c.setCommand(actualCommand)
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

  class DemuxOutputStream(demux: OutputStream) {
    private[this] var channels: List[Channel] = Nil

    private[this] val lock = new Object

    private[this] class Channel(tag: String) extends OutputStream {
      private[this] var buffer: Seq[Byte] = Seq.empty

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

  def copy(in: InputStream, remotePath: File, name: String, length: Long, reportDir: File, sudo: Boolean = false, mode: String = "0644"): Int = {
    val c = s.openChannel("exec").asInstanceOf[ChannelExec]

    import scala.collection.JavaConversions._
    val realIn = new SequenceInputStream(Iterator(
      new ByteArrayInputStream(s"C$mode $length $name\n".getBytes),
      in,
      new ByteArrayInputStream(Array(0.toByte))
    ))
    c.setInputStream(realIn)

    writeFileWithOutputStream(reportDir / "output") { out =>
      val demux = new DemuxOutputStream(out)
      c.setOutputStream(demux.createChannel("O:"))
      c.setErrStream(demux.createChannel("E:"))

      val command = s"${ if ( sudo ) "sudo " else "" }/usr/bin/scp -t $remotePath"

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
}

object Sessions {
  private[this] val jsch = new JSch
  private[this] var sessions = Map.empty[SSHConnectionInfo, Session]

  def get(info: SSHConnectionInfo): Session = {
    sessions.get(info) map { s =>
      if ( s.s.isConnected )
        s
      else {
        sessions -= info
        get(info)
      }
    } getOrElse {
      val s = jsch.getSession(info.username, info.host, info.port)
      s.setUserInfo(new UserInfo {
        override def getPassphrase() = ""
        override def getPassword() = info.password
        override def promptPassword(message: String) = true
        override def promptPassphrase(message: String) = false
        override def promptYesNo(message: String) = false
        override def showMessage(message: String) = println(message)
      })

      val config = new java.util.Properties
      config.put("StrictHostKeyChecking", "no")
      s.setConfig(config)

      s.connect(30) // TODO: configurable

      val session = new Session(s)

      sessions += info -> session

      session
    }
  }

  def shutdown: Unit = {
    sessions.values.foreach { s =>
      s.s.disconnect()
    }
  }
}
