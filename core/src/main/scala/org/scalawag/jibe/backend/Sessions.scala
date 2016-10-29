package org.scalawag.jibe.backend

import com.jcraft.jsch.{ChannelExec, JSch, Session, UserInfo}
import org.scalawag.jibe.Logging

object Sessions {
  private[this] val jsch = new JSch
  private[this] var sessions = Map.empty[SshInfo, Session]

  def get(ssh: SshInfo): Session = synchronized {
    sessions.get(ssh) map { s =>
      if ( s.isConnected ) {
        Logging.log.debug(s"reusing existing session for $ssh: $s")
        s
      } else {
        Logging.log.debug(s"removing disconnected session for $ssh: $s")
        sessions -= ssh
        get(ssh)
      }
    } getOrElse {
      val s = jsch.getSession(ssh.username, ssh.hostname, ssh.port)
      Logging.log.debug(s"created a new session for $ssh: $s")
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
    val s = Sessions.get(ssh)
    val c = s.openChannel("exec").asInstanceOf[ChannelExec]
    try {
      Logging.log.debug(s"opened new channel ${c.getId} on session for $ssh: $s")
      fn(c)
    } finally {
      Logging.log.debug(s"disconnecting channel ${c.getId} on session for $ssh: $s")
      c.disconnect()
    }
  }

  def shutdown: Unit = {
    sessions.values.foreach { s =>
      s.disconnect()
    }
  }
}
