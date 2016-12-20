package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.{StartService, StopService, RestartService, IsServiceRunning}

class StartStopServiceTest extends FunSpec with Matchers with VagrantTest {

  it("Stop cron service") {
    rootSsh.exec(log, s"service cron start")
    rootCommander.execute(StopService("cron"))
    rootCommander.execute(IsServiceRunning("cron")) shouldBe false
  }

  it("Start cron service") {
    rootSsh.exec(log, s"service cron stop")
    rootCommander.execute(StartService("cron"))
    rootCommander.execute(IsServiceRunning("cron")) shouldBe true
  }

  it("Restart cron service") {
    rootSsh.exec(log, s"service cron stop")
    rootCommander.execute(RestartService("cron"))
    rootCommander.execute(IsServiceRunning("cron")) shouldBe true
    rootCommander.execute(RestartService("cron"))
    rootCommander.execute(IsServiceRunning("cron")) shouldBe true
  }
}
