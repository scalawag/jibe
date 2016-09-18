package org.scalawag.jibe.backend.ubuntu

class AddUserToGroupsCommand(user: String, groups: String*) extends ScriptResourceCommand {
  override protected def getScriptContext = Map(
    "targetUser" -> user,
    "targetGroups" -> groups.mkString(" ")
  )
}
