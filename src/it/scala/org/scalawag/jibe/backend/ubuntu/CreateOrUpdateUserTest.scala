package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSpec, Matchers}
import org.scalawag.jibe.mandate.{Group, User}
import org.scalawag.jibe.mandate.command.{CreateOrUpdateGroup, CreateOrUpdateUser, DoesUserExist}

// These tests take advantage of the DoesUserExist command, whose tests should not depend on CreateOrUpdateUser.
// It is assumed that the DoesUserExist command works properly during these tests.

class CreateOrUpdateUserTest extends FunSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with VagrantTest {
  val userA = "userA"
  val groupA = "groupA"
  val groupB = "groupB"

  val user = User(userA, Some(groupA), Some(1400), Some("/tmp"), Some("/bin/false"), Some("Name A"), false)

  override protected def beforeAll() = {
    commander.execute(CreateOrUpdateGroup(Group("groupA")))
    commander.execute(CreateOrUpdateGroup(Group("groupB")))
  }

  before {
    Some(ssh.exec(log, s"userdel $userA")) should contain oneOf(0, 6)
  }

  it("should create the user") {
    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should succeed if the user already exists as specified") {
    commander.execute(CreateOrUpdateUser(user))
    commander.execute(DoesUserExist(user)) shouldBe true
    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the UID if necessary") {
    commander.execute(CreateOrUpdateUser(user.copy(uid = user.uid.map(_ + 1))))
    commander.execute(DoesUserExist(user)) shouldBe false

    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the primary group if necessary") {
    commander.execute(CreateOrUpdateUser(user.copy(primaryGroup = Some(groupB))))
    commander.execute(DoesUserExist(user)) shouldBe false

    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the shell if necessary") {
    commander.execute(CreateOrUpdateUser(user.copy(shell = Some("/bin/true"))))
    commander.execute(DoesUserExist(user)) shouldBe false

    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the home directory if necessary") {
    commander.execute(CreateOrUpdateUser(user.copy(home = Some("/tmp/different"))))
    commander.execute(DoesUserExist(user)) shouldBe false

    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should update the comment if necessary") {
    commander.execute(CreateOrUpdateUser(user.copy(comment = Some("Name B"))))
    commander.execute(DoesUserExist(user)) shouldBe false

    commander.execute(CreateOrUpdateUser(user))

    commander.execute(DoesUserExist(user)) shouldBe true
  }

  it("should ignore user.system when user.uid is specified") {
    val u = User(userA, uid = Some(4000), system = true)

    commander.execute(CreateOrUpdateUser(u))

    commander.execute(DoesUserExist(u)) shouldBe true
  }

  it("should ignore user.system when user already exists") {
    val uWithUid = User(userA, uid = Some(4000))
    val uWithSystem = User(userA, system = true)

    commander.execute(CreateOrUpdateUser(uWithUid))

    commander.execute(CreateOrUpdateUser(uWithSystem))

    // uid should not have been changed
    commander.execute(DoesUserExist(uWithUid)) shouldBe true
  }

  it("should heed user.system (= true) when user doesn't exist and user.uid is not specified") {
    val u = User(userA, system = true)

    commander.execute(CreateOrUpdateUser(u))

    ssh.exec(log, s"test $$( getent passwd $userA | cut -d: -f3 ) -lt 1000") shouldBe 0
  }

  it("should heed user.system (= false) when user doesn't exist and user.uid is not specified") {
    val u = User(userA, system = false)

    commander.execute(CreateOrUpdateUser(u))

    ssh.exec(log, s"test $$( getent passwd $userA | cut -d: -f3 ) -ge 1000") shouldBe 0
  }

  it("should ignore user.system for existence test") {
    ssh.exec(log, s"useradd $userA") shouldBe 0

    commander.execute(CreateOrUpdateUser(User(userA, system = true)))
  }

  it("should throw when the user name is invalid") {
    an [Exception] should be thrownBy commander.execute(CreateOrUpdateUser(User("illegal:user name")))
  }

  it("should throw when the UID is invalid") {
    an [Exception] should be thrownBy commander.execute(CreateOrUpdateUser(User(userA, uid = Some(-14))))
  }
}
