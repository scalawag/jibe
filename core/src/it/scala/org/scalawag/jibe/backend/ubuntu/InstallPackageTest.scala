package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.{InstallPackage, IsPackageInstalled}

class InstallPackageTest extends FunSpec with Matchers with VagrantTest {

  it("Install vim") {
    rootSsh.exec(log, s"apt-get remove --assume-yes vim")
    rootCommander.execute(InstallPackage("vim"))
    rootCommander.execute(IsPackageInstalled("vim")) shouldBe true
  }

}
