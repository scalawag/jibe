package org.scalawag.jibe.mandate

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.FileContent

trait WriteRemoteFileBaseTest extends FunSpec with Matchers with MandateTest {

  def generateTests(mandate: WriteRemoteFileBase, content: FileContent): Unit = {
    val destination = mandate.remotePath

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

    describe("takeAction") {

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
}
