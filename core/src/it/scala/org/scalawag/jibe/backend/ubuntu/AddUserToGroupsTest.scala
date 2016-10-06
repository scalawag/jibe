package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSpec, Matchers}
import org.scalawag.jibe.mandate.command._

class AddUserToGroupsTest extends FunSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with VagrantTest {
  val groupA = "groupA"
  val groupB = "groupB"
  val groupC = "groupC"
  val userA = "userA"

  override protected def beforeAll() = {
    rootCommander.execute(CreateOrUpdateGroup(Group(groupA)))
    rootCommander.execute(CreateOrUpdateGroup(Group(groupB)))
    rootCommander.execute(CreateOrUpdateGroup(Group(groupC)))
    rootCommander.execute(DeleteUser(userA))
    rootCommander.execute(CreateOrUpdateUser(User(userA)))
  }

  before {
    rootSsh.exec(log, s"usermod $userA -G ''") shouldBe 0
  }

  it("should add user to a single group") {
    commander.execute(IsUserInAllGroups(userA, Set(groupA))) shouldBe false

    rootCommander.execute(AddUserToGroups(userA, Set(groupA)))

    commander.execute(IsUserInAllGroups(userA, Set(groupA))) shouldBe true
  }

  it("should add user to a multiple groups") {
    commander.execute(IsUserInAllGroups(userA, Set(groupA, groupB))) shouldBe false

    rootCommander.execute(AddUserToGroups(userA, Set(groupA, groupB)))

    commander.execute(IsUserInAllGroups(userA, Set(groupA, groupB))) shouldBe true
  }

  it("should add user to some new groups while leaving old ones alone") {
    rootCommander.execute(AddUserToGroups(userA, Set(groupA)))
    commander.execute(IsUserInAllGroups(userA, Set(groupA, groupB))) shouldBe false

    rootCommander.execute(AddUserToGroups(userA, Set(groupA, groupB)))

    commander.execute(IsUserInAllGroups(userA, Set(groupA, groupB))) shouldBe true
  }

  it("should leave user in all existing groups") {
    rootCommander.execute(AddUserToGroups(userA, Set(groupA, groupB)))
    commander.execute(IsUserInAllGroups(userA, Set(groupA, groupB))) shouldBe true

    rootCommander.execute(AddUserToGroups(userA, Set(groupA, groupB)))

    commander.execute(IsUserInAllGroups(userA, Set(groupA, groupB))) shouldBe true
  }

  it("should throw when not run as root") {
    an [Exception] should be thrownBy commander.execute(AddUserToGroups(userA, Set(groupA)))
  }
}
