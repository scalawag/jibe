package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.mandate.Group

class CreateOrUpdateGroupCommand(group: Group) extends ScriptResourceCommand {
  override protected val getScriptContext = caseClassToContext("group", group)
}
