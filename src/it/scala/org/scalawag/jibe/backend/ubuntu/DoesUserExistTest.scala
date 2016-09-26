package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSpec, Matchers}
import org.scalawag.jibe.mandate.{Group, User}
import org.scalawag.jibe.mandate.command.{CreateOrUpdateGroup, DoesUserExist}

class DoesUserExistTest extends FunSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with VagrantTest {
  val userA = "userA"
  val groupA = "groupA"
  val groupB = "groupB"

  override protected def beforeAll() = {
    commander.execute(CreateOrUpdateGroup(Group("groupA")))
    commander.execute(CreateOrUpdateGroup(Group("groupB")))
  }

  before {
    Some(ssh.exec(log, s"userdel $userA")) should contain oneOf(0, 6)
  }

  it("should detect if the user does not exist") {
    commander.execute(DoesUserExist(User(userA))) shouldBe false
  }

  it("should detect if the user exists") {
    ssh.exec(log, s"useradd $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA))) shouldBe true
  }

  it("should detect if the user exists but has the wrong UID") {
    ssh.exec(log, s"useradd -u 4000 $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, uid = Some(4001)))) shouldBe false
  }

  it("should detect if the user exists and has the right UID") {
    ssh.exec(log, s"useradd -u 4000 $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, uid = Some(4000)))) shouldBe true
  }

  it("should detect if the user exists but has the wrong primary group") {
    ssh.exec(log, s"useradd -g $groupA $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, primaryGroup = Some(groupB)))) shouldBe false
  }

  it("should detect if the user exists and has the right primary group") {
    ssh.exec(log, s"useradd -g $groupA $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, primaryGroup = Some(groupA)))) shouldBe true
  }

  it("should detect if the user exists but has the wrong home directory") {
    ssh.exec(log, s"useradd -d /tmp/a $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, home = Some("/tmp/b")))) shouldBe false
  }

  it("should detect if the user exists and has the right home directory") {
    ssh.exec(log, s"useradd -d /tmp/a $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, home = Some("/tmp/a")))) shouldBe true
  }

  it("should detect if the user exists but has the wrong shell") {
    ssh.exec(log, s"useradd -s /bin/false $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, shell = Some("/bin/true")))) shouldBe false
  }

  it("should detect if the user exists and has the right shell") {
    ssh.exec(log, s"useradd -s /bin/false $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, shell = Some("/bin/false")))) shouldBe true
  }

  it("should detect if the user exists but has the wrong comment") {
    ssh.exec(log, s"useradd -c 'Name A' $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, comment = Some("Name B")))) shouldBe false
  }

  it("should detect if the user exists and has the right comment") {
    ssh.exec(log, s"useradd -c 'Name A' $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, comment = Some("Name A")))) shouldBe true
  }

  it("should ignore user.system for existence test") {
    ssh.exec(log, s"useradd $userA") shouldBe 0

    commander.execute(DoesUserExist(User(userA, system = true))) shouldBe true
  }
}
