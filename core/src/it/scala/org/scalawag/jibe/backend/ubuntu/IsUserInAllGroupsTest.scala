package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSpec, Matchers}
import org.scalawag.jibe.mandate.command._

class IsUserInAllGroupsTest extends FunSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with VagrantTest {
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

  it("should detect presence of user in a single group") {
    rootSsh.exec(log, s"usermod $userA -G '$groupA'") shouldBe 0

    rootCommander.execute(IsUserInAllGroups(userA, Seq(groupA))) shouldBe true
  }

  it("should detect presence of user in multiple groups") {
    Some(rootSsh.exec(log, s"usermod $userA -G '$groupA,$groupB'")) should contain oneOf(0, 6)

    rootCommander.execute(IsUserInAllGroups(userA, Seq(groupA, groupB))) shouldBe true
  }

  it("should detect presence of the user's primary group") {
    rootCommander.execute(IsUserInAllGroups(userA, Seq(userA))) shouldBe true
  }

  it("should detect absence of the user from a single group") {
    rootCommander.execute(IsUserInAllGroups(userA, Seq(groupA))) shouldBe false
  }

  it("should detect absence of the user from a at least one specified group") {
    rootSsh.exec(log, s"usermod $userA -G '$groupA'") shouldBe 0

    rootCommander.execute(IsUserInAllGroups(userA, Seq(groupA, groupB))) shouldBe false
  }
}
