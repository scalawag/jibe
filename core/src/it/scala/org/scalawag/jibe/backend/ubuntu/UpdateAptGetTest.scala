package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.UpdateAptGet
import scala.concurrent.duration._

class UpdateAptGetTest extends FunSpec with Matchers with VagrantTest {

  it("apt-get update should not run when current age is smaller than refreshInterval") {
    rootSsh.exec(log, s"touch /var/cache/apt/pkgcache.bin")
    rootSsh.exec(log, s"stat -c %Y /var/cache/apt/pkgcache.bin > /tmp/UpdateAptGetPre.txt")
    rootCommander.execute(UpdateAptGet(1.day))
    rootSsh.exec(log, s"stat -c %Y /var/cache/apt/pkgcache.bin > /tmp/UpdateAptGetPost.txt")
    rootSsh.exec(log, s"diff /tmp/UpdateAptGetPre.txt /tmp/UpdateAptGetPost.txt") shouldBe 0
  }

  it("apt-get update should run when current age is larger than refreshInterval") {
    rootSsh.exec(log, s"touch --date='2000-10-01 18:49:45.981316728 +0000' /var/cache/apt/pkgcache.bin")
    rootSsh.exec(log, s"stat -c %Y /var/cache/apt/pkgcache.bin > /tmp/UpdateAptGetPre.txt")
    rootCommander.execute(UpdateAptGet(1.day))
    rootSsh.exec(log, s"stat -c %Y /var/cache/apt/pkgcache.bin > /tmp/UpdateAptGetPost.txt")
    rootSsh.exec(log, s"diff /tmp/UpdateAptGetPre.txt /tmp/UpdateAptGetPost.txt") shouldBe 1
  }

}
