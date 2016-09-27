package org.scalawag.jibe.backend.ubuntu

import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}
import org.scalawag.jibe.mandate.Group
import org.scalawag.jibe.mandate.command.DoesGroupExist

class DoesGroupExistTest extends FunSpec with Matchers with BeforeAndAfter with VagrantTest {
  val groupName = "groupA"

  before {
    Some(ssh.exec(log, s"groupdel $groupName")) should contain oneOf(0, 6)
  }

  it("should detect if the group does not exist") {
    commander.execute(DoesGroupExist(Group(groupName))) shouldBe false
  }

  it("should detect if the group exists") {
    ssh.exec(log, s"groupadd $groupName") shouldBe 0

    commander.execute(DoesGroupExist(Group(groupName))) shouldBe true
  }

  it("should detect if the group exists but has the wrong GID") {
    ssh.exec(log, s"groupadd -g 4000 $groupName") shouldBe 0

    commander.execute(DoesGroupExist(Group(groupName, Some(4001)))) shouldBe false
  }

  it("should detect if the group exists and has the right GID") {
    ssh.exec(log, s"groupadd -g 4000 $groupName") shouldBe 0

    commander.execute(DoesGroupExist(Group(groupName, Some(4000)))) shouldBe true
  }

  it("should ignore group.system for existence test") {
    ssh.exec(log, s"groupadd $groupName") shouldBe 0

    commander.execute(DoesGroupExist(Group(groupName, system = true))) shouldBe true
  }

  it("should throw when the group name is invalid") {
    an [Exception] should be thrownBy commander.execute(DoesGroupExist(Group("illegal:group name")))
  }
}