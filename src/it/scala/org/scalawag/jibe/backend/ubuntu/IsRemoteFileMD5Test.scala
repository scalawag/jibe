package org.scalawag.jibe.backend.ubuntu

import java.io.File

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.{IsRemoteFileLength, IsRemoteFileMD5}

class IsRemoteFileMD5Test extends FunSpec with Matchers with VagrantTest {
  val file = new File("/tmp/IsRemoteFileMD5Test.dat")

  val md5 = "d41d8cd98f00b204e9800998ecf8427e" // MD5 of zero-length file

  it("should return false if the file does not exist") {
    ssh.exec(log, s"rm -f $file") shouldBe 0

    commander.execute(IsRemoteFileMD5(file, md5)) shouldBe false
  }

  it("should return false if the file exists and has the wrong MD5") {
    ssh.exec(log, s"cat /dev/null > $file") shouldBe 0

    commander.execute(IsRemoteFileMD5(file, md5.replace('d', 'a'))) shouldBe false
  }

  it("should return true if the file exists and has the right MD5") {
    ssh.exec(log, s"cat /dev/null > $file") shouldBe 0

    commander.execute(IsRemoteFileMD5(file, md5)) shouldBe true
  }
}
