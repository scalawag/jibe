package org.scalawag.jibe.mandate

import java.io.File

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.TestLogging
import org.scalawag.jibe.backend.Commander

class WriteRemoteFileTest extends FunSpec with Matchers with MockFactory {
  val log = TestLogging.log
  val commander = mock[Commander]
  val file: File = null
  val destination = new File("/tmp/destination")
  val content = command.FileContent("hello")
  val sot = WriteRemoteFile(destination, content)
  val lengthTest = command.IsRemoteFileLength(destination, 5)
  val md5Test = command.IsRemoteFileMD5(destination, "5d41402abc4b2a76b9719d911017c592")
  val writeCommand = command.WriteRemoteFile(destination, content)
  val ex = new RuntimeException("BOOM")

  implicit val context = MandateExecutionContext(commander, file, log)

  def executing(cmd: command.BooleanCommand) =
    (commander.execute(_: command.BooleanCommand)(_: MandateExecutionContext)).expects(cmd, context)

  def executing(cmd: command.UnitCommand) =
    (commander.execute(_: command.UnitCommand)(_: MandateExecutionContext)).expects(cmd, context)

  describe("isActionCompleted") {

    it("should short-circuit the MD5 check on different size") {
      executing(lengthTest).returns(false).once

      sot.isActionCompleted shouldBe false
    }

    it("should require the MD5 check on same size and then decide (true)") {
      executing(lengthTest).returns(true).once
      executing(md5Test).returns(true).once

      sot.isActionCompleted shouldBe true
    }

    it("should require the MD5 check on same size and then decide (false)") {
      executing(lengthTest).returns(true).once
      executing(md5Test).returns(false).once

      sot.isActionCompleted shouldBe false
    }

    it("should fail when the IsRemoteFileLength command throws") {
      executing(lengthTest).throws(ex).once

      intercept[Exception] {
        sot.isActionCompleted
      } shouldBe ex
    }

    it("should fail when the IsRemoteFileMD5 command throws") {
      executing(lengthTest).returns(true).once
      executing(md5Test).throws(ex).once

      intercept[Exception] {
        sot.isActionCompleted
      } shouldBe ex
    }
  }

  describe("takeAction") {

    it("should execute the WriteRemoteFile command successfully") {
      executing(writeCommand).once

      sot.takeAction
    }

    it("should fail when the WriteRemoteFile command throws") {
      executing(writeCommand).throws(ex).once

      intercept[Exception] {
        sot.takeAction
      } shouldBe ex
    }
  }
}
