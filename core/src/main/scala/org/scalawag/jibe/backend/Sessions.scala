package org.scalawag.jibe.backend

import com.jcraft.jsch.{ChannelExec, JSch, Session, UserInfo}

object Sessions {
  private[this] val jsch = new JSch
  private[this] var sessions = Map.empty[SshInfo, Session]

  def get(ssh: SshInfo): Session = {
    sessions.get(ssh) map { s =>
      if ( s.isConnected )
        s
      else {
        sessions -= ssh
        get(ssh)
      }
    } getOrElse {
      val s = jsch.getSession(ssh.username, ssh.hostname, ssh.port)
      s.setUserInfo(new UserInfo {
        override def getPassphrase() = ""
        override def getPassword() = ssh.password
        override def promptPassword(message: String) = true
        override def promptPassphrase(message: String) = false
        override def promptYesNo(message: String) = false
        override def showMessage(message: String) = println(message)
      })

      val config = new java.util.Properties
      config.put("StrictHostKeyChecking", "no")
      s.setConfig(config)

      s.connect(30) // TODO: configurable

      sessions += ssh -> s

      s
    }
  }

  def withChannel[A](ssh: SshInfo)(fn: ChannelExec => A): A = {
    val c = Sessions.get(ssh).openChannel("exec").asInstanceOf[ChannelExec]
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
