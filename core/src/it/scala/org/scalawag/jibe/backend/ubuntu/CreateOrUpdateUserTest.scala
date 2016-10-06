package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSpec, Matchers}
import org.scalawag.jibe.mandate.command._

// These tests take advantage of the DoesUserExist command, whose tests should not depend on CreateOrUpdateUser.
// It is assumed that the DoesUserExist command works properly during these tests.

class CreateOrUpdateUserTest extends FunSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with VagrantTest {
  val userA = "userA"
  val groupA = "groupA"
  val groupB = "groupB"

  val user = User(userA, Some(groupA), Some(1400), Some("/tmp"), Some("/bin/false"), Some("Name A"), false)

  override protected def beforeAll() = {
    rootCommander.execute(CreateOrUpdateGroup(Group("groupA")))
    rootCommander.execute(CreateOrUpdateGroup(Group("groupB")))
  }

  before {
    Some(rootSsh.exec(log, s"userdel $userA")) should contain oneOf(0, 6)
  }

  it("should create the user") {
    rootCommander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should succeed if the user already exists as specified") {
    rootCommander.execute(CreateOrUpdateUser(user))
    commander.execute(DoesUserExist(user)) shouldBe true
    commander.execute(CreateOrUpdateUser(user)) // root not needed if no changes are found

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the UID if necessary") {
    rootCommander.execute(CreateOrUpdateUser(user.copy(uid = user.uid.map(_ + 1))))
    commander.execute(DoesUserExist(user)) shouldBe false

    rootCommander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the primary group if necessary") {
    rootCommander.execute(CreateOrUpdateUser(user.copy(primaryGroup = Some(groupB))))
    commander.execute(DoesUserExist(user)) shouldBe false

    rootCommander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the shell if necessary") {
    rootCommander.execute(CreateOrUpdateUser(user.copy(shell = Some("/bin/true"))))
    commander.execute(DoesUserExist(user)) shouldBe false

    rootCommander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the home directory if necessary") {
    rootCommander.execute(CreateOrUpdateUser(user.copy(home = Some("/tmp/different"))))
    commander.execute(DoesUserExist(user)) shouldBe false

    rootCommander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the comment if necessary") {
    rootCommander.execute(CreateOrUpdateUser(user.copy(comment = Some("Name B"))))
    commander.execute(DoesUserExist(user)) shouldBe false

    rootCommander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should ignore user.system when user.uid is specified") {
    val u = User(userA, uid = Some(4000), system = true)

    rootCommander.execute(CreateOrUpdateUser(u))

    commander.execute(DoesUserExist(u)) shouldBe true
  }

  it("should ignore user.system when user already exists") {
    val uWithUid = User(userA, uid = Some(4000))
    val uWithSystem = User(userA, system = true)

    rootCommander.execute(CreateOrUpdateUser(uWithUid))

    rootCommander.execute(CreateOrUpdateUser(uWithSystem))

    // uid should not have been changed
    commander.execute(DoesUserExist(uWithUid)) shouldBe true
  }

  it("should heed user.system (= true) when user doesn't exist and user.uid is not specified") {
    val u = User(userA, system = true)

    rootCommander.execute(CreateOrUpdateUser(u))

    rootSsh.exec(log, s"test $$( getent passwd $userA | cut -d: -f3 ) -lt 1000") shouldBe 0
  }

  it("should heed user.system (= false) when user doesn't exist and user.uid is not specified") {
    val u = User(userA, system = false)

    rootCommander.execute(CreateOrUpdateUser(u))

    rootSsh.exec(log, s"test $$( getent passwd $userA | cut -d: -f3 ) -ge 1000") shouldBe 0
  }

  it("should ignore user.system for existence test") {
    rootSsh.exec(log, s"useradd $userA") shouldBe 0

    rootCommander.execute(CreateOrUpdateUser(User(userA, system = true)))
  }

  it("should throw when the user name is invalid") {
    an [Exception] should be thrownBy rootCommander.execute(CreateOrUpdateUser(User("illegal:user name")))
  }

  it("should throw when the UID is invalid") {
    an [Exception] should be thrownBy rootCommander.execute(CreateOrUpdateUser(User(userA, uid = Some(-14))))
  }

  it("should throw when not run as root") {
    an [Exception] should be thrownBy commander.execute(CreateOrUpdateUser(userA))
  }
}
