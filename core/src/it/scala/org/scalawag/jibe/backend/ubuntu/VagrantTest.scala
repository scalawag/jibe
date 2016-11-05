package org.scalawag.jibe.backend.ubuntu

import java.io.File

import org.scalawag.jibe.IntegrationTestLogging
import org.scalawag.jibe.backend.{SecureShellBackend, SshInfo}
import org.scalawag.jibe.multitree.MandateExecutionContext

trait VagrantTest {
  val log = IntegrationTestLogging.log
  val sshInfo = SshInfo("192.168.212.11", "vagrant", "vagrant", 22)
  val rootCommander = new UbuntuCommander(sshInfo, true)
  val rootSsh = new SecureShellBackend(sshInfo, true)
  val commander = new UbuntuCommander(sshInfo, false)
  val ssh = new SecureShellBackend(sshInfo, false)
  implicit val context = MandateExecutionContext(rootCommander, log)
}
