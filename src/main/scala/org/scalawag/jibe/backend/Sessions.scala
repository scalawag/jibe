package org.scalawag.jibe.backend

import com.jcraft.jsch.{ChannelExec, JSch, Session, UserInfo}

object Sessions {
  private[this] val jsch = new JSch
  private[this] var sessions = Map.empty[Target, Session]

  def get(target: Target): Session = {
    sessions.get(target) map { s =>
      if ( s.isConnected )
        s
      else {
        sessions -= target
        get(target)
      }
    } getOrElse {
      val s = jsch.getSession(target.username, target.hostname, target.port)
      s.setUserInfo(new UserInfo {
        override def getPassphrase() = ""
        override def getPassword() = target.password
        override def promptPassword(message: String) = true
        override def promptPassphrase(message: String) = false
        override def promptYesNo(message: String) = false
        override def showMessage(message: String) = println(message)
      })

      val config = new java.util.Properties
      config.put("StrictHostKeyChecking", "no")
      s.setConfig(config)

      s.connect(30) // TODO: configurable

      sessions += target -> s

      s
    }
  }

  def withChannel[A](target: Target)(fn: ChannelExec => A): A = {
    val c = Sessions.get(target).openChannel("exec").asInstanceOf[ChannelExec]
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
