package org.scalawag.jibe.backend

import java.io.{ByteArrayOutputStream, File, FileOutputStream}

import com.jcraft.jsch.{ChannelExec, JSch, UserInfo}
import org.scalawag.jibe.FileUtils._

class Session(val s: com.jcraft.jsch.Session) {
  // Command can include bash scripting with ';' and '&&' and can use environment variables

  def execute(command: String, reportDir: File, sudo: Boolean = false): Int = {
    val c = s.openChannel("exec").asInstanceOf[ChannelExec]
    c.setInputStream(null)

    writeFileWithOutputStream(reportDir / "stdout") { out =>
      writeFileWithOutputStream(reportDir / "stderr") { err =>

        c.setOutputStream(out)
        c.setErrStream(err)

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
