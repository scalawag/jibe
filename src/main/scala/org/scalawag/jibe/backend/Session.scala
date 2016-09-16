package org.scalawag.jibe.backend

import com.jcraft.jsch.{ChannelExec, JSch, Session, UserInfo}

object Sessions {
  private[this] val jsch = new JSch
  private[this] var sessions = Map.empty[SSHConnectionInfo, Session]

  def get(info: SSHConnectionInfo): Session = {
    sessions.get(info) map { s =>
      if ( s.isConnected )
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

      sessions += info -> s

      s
    }
  }

  def withChannel[A](sshConnectionInfo: SSHConnectionInfo)(fn: ChannelExec => A): A = {
    val c = Sessions.get(sshConnectionInfo).openChannel("exec").asInstanceOf[ChannelExec]
    try {
      fn(c)
    } finally {
      c.disconnect()
    }
  }

  def shutdown: Unit = {
    sessions.values.foreach { s =>
      s.disconnect()
    }
  }
}
