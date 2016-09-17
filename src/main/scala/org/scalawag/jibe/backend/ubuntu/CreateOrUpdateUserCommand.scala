package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.mandate.User

class CreateOrUpdateUserCommand(user: User) extends ScriptResourceCommand with BashCommands {
  override protected val getScriptContext = caseClassToContext("user", user)
}
