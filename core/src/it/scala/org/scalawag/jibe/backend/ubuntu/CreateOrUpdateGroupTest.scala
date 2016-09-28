package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.{CreateOrUpdateGroup, DoesGroupExist}
import org.scalawag.jibe.mandate.Group

// These tests take advantage of the DoesGroupExist command, whose tests should not depend on CreateOrUpdateGroup.
// It is assumed that the DoesGroupExist command works properly during these tests.

class CreateOrUpdateGroupTest extends FunSpec with Matchers with BeforeAndAfter with VagrantTest {
  val groupName = "groupB"

  before {
    Some(rootSsh.exec(log, s"groupdel $groupName")) should contain oneOf(0, 6)
  }

  it("should create the group") {
    val g = Group(groupName)

    rootCommander.execute(CreateOrUpdateGroup(g))

    commander.execute(DoesGroupExist(g)) shouldBe true
  }

  it("should succeed if the group already exists") {
    val g = Group(groupName)

    rootCommander.execute(CreateOrUpdateGroup(g))
    commander.execute(DoesGroupExist(g)) shouldBe true
    commander.execute(CreateOrUpdateGroup(g)) // root not needed if no changes are found

    commander.execute(DoesGroupExist(g)) shouldBe true
  }

  it("should use the specified GID when creating the group") {
    val g = Group(groupName, Some(4000))

    rootCommander.execute(CreateOrUpdateGroup(g))

    commander.execute(DoesGroupExist(g)) shouldBe true
  }

  it("should modify the GID if it's not the one specified") {
    val gBefore = Group(groupName, Some(4000))
    val gAfter = gBefore.copy(gid = Some(4001))

    rootCommander.execute(CreateOrUpdateGroup(gBefore))

    rootCommander.execute(CreateOrUpdateGroup(gAfter))

    commander.execute(DoesGroupExist(gAfter)) shouldBe true
  }

  it("should ignore group.system when group.gid is specified") {
    val g = Group(groupName, Some(4000), true)

    rootCommander.execute(CreateOrUpdateGroup(g))

    commander.execute(DoesGroupExist(g)) shouldBe true
  }

  it("should ignore group.system when group already exists") {
    val gWithGid = Group(groupName, gid = Some(4000))
    val gWithSystem = Group(groupName, system = true)

    rootCommander.execute(CreateOrUpdateGroup(gWithGid))

    rootCommander.execute(CreateOrUpdateGroup(gWithSystem))

    // gid should not have been changed
    commander.execute(DoesGroupExist(gWithGid)) shouldBe true
  }

  it("should heed group.system (= true) when group doesn't exist and group.gid is not specified") {
    val g = Group(groupName, system = true)

    rootCommander.execute(CreateOrUpdateGroup(g))

    ssh.exec(log, s"test $$( getent group $groupName | cut -d: -f3 ) -lt 1000") shouldBe 0
  }

  it("should heed group.system (= false) when group doesn't exist and group.gid is not specified") {
    val g = Group(groupName, system = false)

    rootCommander.execute(CreateOrUpdateGroup(g))

    rootSsh.exec(log, s"test $$( getent group $groupName | cut -d: -f3 ) -ge 1000") shouldBe 0
  }

  it("should ignore group.system for existence test") {
    rootSsh.exec(log, s"groupadd $groupName") shouldBe 0

    commander.execute(CreateOrUpdateGroup(Group(groupName, system = true)))
  }

  it("should throw when the group name is invalid") {
    an [Exception] should be thrownBy rootCommander.execute(CreateOrUpdateGroup(Group("illegal:group name")))
  }

  it("should throw when the GID is invalid") {
    an [Exception] should be thrownBy rootCommander.execute(CreateOrUpdateGroup(Group(groupName, Some(-14))))
  }

  it("should throw when not run as root") {
    an [Exception] should be thrownBy commander.execute(CreateOrUpdateGroup(Group(groupName)))
  }
}
