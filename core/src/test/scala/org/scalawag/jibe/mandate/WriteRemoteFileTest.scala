package org.scalawag.jibe.mandate

import java.io.File

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers}

class WriteRemoteFileTest extends FunSpec with MandateTest with Matchers with MockFactory {
  val content = command.FileContent("hello")
  val destination = new File("/tmp/destination")
  val mandate = WriteRemoteFile(destination, content)

  val lengthTest = command.IsRemoteFileLength(destination, content.length)
  val md5Test = command.IsRemoteFileMD5(destination, content.md5)
  val writeCommand = command.WriteRemoteFile(destination, content)

  val ex = new RuntimeException("BOOM")

  describe("isActionCompleted") {

    it("should short-circuit the MD5 check on different size") {
      executing(lengthTest).returns(false).once

      mandate.isActionCompleted shouldBe false
    }

    it("should require the MD5 check on same size and then decide (true)") {
      executing(lengthTest).returns(true).once
      executing(md5Test).returns(true).once

      mandate.isActionCompleted shouldBe true
    }

    it("should require the MD5 check on same size and then decide (false)") {
      executing(lengthTest).returns(true).once
      executing(md5Test).returns(false).once

      mandate.isActionCompleted shouldBe false
    }

    it("should fail when the IsRemoteFileLength command throws") {
      executing(lengthTest).throws(ex).once

      intercept[Exception] {
        mandate.isActionCompleted
      } shouldBe ex
    }

    it("should fail when the IsRemoteFileMD5 command throws") {
      executing(lengthTest).returns(true).once
      executing(md5Test).throws(ex).once

      intercept[Exception] {
        mandate.isActionCompleted
      } shouldBe ex
    }
  }

  describe("takeActionIfNeeded") {

    it("should execute the WriteRemoteFile command successfully") {
      executing(writeCommand).once

      mandate.takeAction
    }

    it("should fail when the WriteRemoteFile command throws") {
      executing(writeCommand).throws(ex).once

      intercept[Exception] {
        mandate.takeAction
      } shouldBe ex
    }
  }
}
