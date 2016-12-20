package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.{StartService, IsServiceRunning}

class StartStopServiceTest extends FunSpec with Matchers with VagrantTest {

  it("Stop ufw service") {
    rootSsh.exec(log, s"service ufw start")
    rootCommander.execute(StopService("ufw"))
    rootCommander.execute(IsServiceRunning("ufw")) shouldBe true
  }

  it("Start ufw service") {
    rootSsh.exec(log, s"service ufw stop")
    rootCommander.execute(StartService("ufw"))
    rootCommander.execute(IsServiceRunning("ufw")) shouldBe true
  }
}
