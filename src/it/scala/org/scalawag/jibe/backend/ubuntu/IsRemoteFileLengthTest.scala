package org.scalawag.jibe.backend.ubuntu

import java.io.File

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.IsRemoteFileLength

class IsRemoteFileLengthTest extends FunSpec with Matchers with VagrantTest {
  val file = new File("/tmp/IsRemoteFileLengthTest.dat")

  it("should return false if the file does not exist") {
    rootSsh.exec(log, s"rm -f $file") shouldBe 0

    commander.execute(IsRemoteFileLength(file, 0)) shouldBe false
  }

  it("should return false if the file exists and has the wrong length") {
    rootSsh.exec(log, s"cat /dev/null > $file") shouldBe 0

    commander.execute(IsRemoteFileLength(file, 1)) shouldBe false
  }

  it("should return true if the file exists and has the right length") {
    rootSsh.exec(log, s"cat /dev/null > $file") shouldBe 0

    commander.execute(IsRemoteFileLength(file, 0)) shouldBe true
  }
}
