package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}
import org.scalawag.jibe.mandate.command.{Group, User, CreateOrUpdateUser, DeleteUser, DoesUserExist}

// These tests take advantage of the DoesUserExist and CreateOrUpdateUser commands, whose tests should not depend on
// DeleteUser. It is assumed that the these other commands work properly during these tests, as they are tested
// independently.

class DeleteUserTest extends FunSpec with Matchers with VagrantTest {
  val userA = "userA"

  it("should delete the user, if it exists") {
    rootCommander.execute(CreateOrUpdateUser(userA))
    commander.execute(DoesUserExist(userA)) shouldBe true

    rootCommander.execute(DeleteUser(userA))

    commander.execute(DoesUserExist(userA)) shouldBe false
  }

  it("should silently do nothing if the user does not exist") {
    rootCommander.execute(DeleteUser(userA))

    commander.execute(DoesUserExist(userA)) shouldBe false

    commander.execute(DeleteUser(userA))

    commander.execute(DoesUserExist(userA)) shouldBe false
  }

  it("should fail if the user lacks permission") {
    rootCommander.execute(CreateOrUpdateUser(userA))

    an [Exception] should be thrownBy commander.execute(DeleteUser(userA))
  }
}
