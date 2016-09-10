package org.scalawag.jibe.backend

import java.io.ByteArrayOutputStream

import com.jcraft.jsch.{ChannelExec, JSch, UserInfo}

class Session(val s: com.jcraft.jsch.Session) {
  // Command can include bash scripting with ';' and '&&' and can use environment variables

  def execute(command: String): CommandResults = {
    val c = s.openChannel("exec").asInstanceOf[ChannelExec]

    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()

    c.setInputStream(null)

    c.setOutputStream(out)
    c.setErrStream(err)

    c.setCommand(command)
    c.connect()

    while ( !c.isClosed )
      Thread.sleep(50)

    c.disconnect()

    CommandResults(command, c.getExitStatus, out.toString, err.toString)
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
