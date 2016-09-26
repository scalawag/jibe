package org.scalawag.jibe.backend.ubuntu

import java.io.File

import org.scalawag.jibe.IntegrationTestLogging
import org.scalawag.jibe.backend.{SecureShellBackend, SshInfo}
import org.scalawag.jibe.mandate.MandateExecutionContext

trait VagrantTest {
  val log = IntegrationTestLogging.log
  val sshInfo = SshInfo("192.168.212.11", "vagrant", "vagrant", 22)
  val commander = new UbuntuCommander(sshInfo, true)
  val ssh = new SecureShellBackend(sshInfo, true)
  implicit val context = MandateExecutionContext(commander, new File("/tmp/"), log)
}
