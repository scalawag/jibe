package org.scalawag.jibe.mandate

import java.io.File

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers}

class WriteRemoteFileTest extends FunSpec with WriteRemoteFileBaseTest with Matchers with MockFactory {
  val content = command.FileContent("hello")
  val destination = new File("/tmp/destination")

  generateTests(WriteRemoteFile(destination, content), content)
}
