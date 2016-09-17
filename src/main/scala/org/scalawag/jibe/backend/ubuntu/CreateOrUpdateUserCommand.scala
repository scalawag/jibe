package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.mandate.User

class CreateOrUpdateUserCommand(user: User) extends ScriptResourceCommand with BashCommands {

  override protected def getScriptContext = Map(
    "user_name" -> user.name,
    "user_group" -> user.primaryGroup.getOrElse(""),
    "user_uid" -> user.uid.map(_.toString).getOrElse(""),
    "user_home" -> user.home.getOrElse(""),
    "user_shell" -> user.shell.getOrElse(""),
    "user_comment" -> user.comment.getOrElse(""),
    "user_system" -> ( if ( user.system ) "t" else "" )
  )

}
