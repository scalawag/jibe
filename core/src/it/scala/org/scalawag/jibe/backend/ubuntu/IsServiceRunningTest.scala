package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.IsServiceRunning

class IsServiceRunningTest extends FunSpec with Matchers with VagrantTest {

  it("non-existent service throws exception") {
    an [Exception] should be thrownBy rootCommander.execute(IsServiceRunning("servicedoesntexist"))
  }

  it("empty service name throws execption") {
    an [Exception] should be thrownBy rootCommander.execute(IsServiceRunning(""))
  }

  it("ssh doesn't need to be started because it's already running") {
    rootSsh.exec(log, s"service ssh start")
    rootCommander.execute(IsServiceRunning("ssh")) shouldBe true // true = 0
  }

  it("cron service needs to be started when it's not running") {
    rootSsh.exec(log, s"service cron stop")
    rootCommander.execute(IsServiceRunning("cron")) shouldBe false // false = 1
  }

}
