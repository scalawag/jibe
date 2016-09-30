package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.IsPackageInstalled

class IsPackageInstalledTest extends FunSpec with Matchers with VagrantTest {

  it("non-existent packages throws exception") {
    an [Exception] should be thrownBy rootCommander.execute(IsPackageInstalled("packagedoesntexist"))
  }

  it("empty package name throws execption") {
    an [Exception] should be thrownBy rootCommander.execute(IsPackageInstalled(""))
  }

  it("vim does not need to be installed when it is already there, no version is specified, and no update is available") {
    rootSsh.exec(log, s"apt-get install --assume-yes vim")
    rootCommander.execute(IsPackageInstalled("vim")) shouldBe true // true = 0
  }

  it("vim does need to be installed when it is not already there (no version specified)") {
    rootSsh.exec(log, s"apt-get remove --assume-yes vim")
    rootCommander.execute(IsPackageInstalled("vim")) shouldBe false // false = 1
  }

}
