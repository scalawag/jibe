package org.scalawag.jibe.backend.ubuntu

import org.scalawag.jibe.mandate.Group

class CreateOrUpdateGroupCommand(group: Group) extends ScriptResourceCommand with BashCommands {
  override protected val getScriptContext = Map(
    "group_name" -> group.name,
    "group_gid" -> group.gid.map(_.toString).getOrElse(""),
    "group_system" -> ( if ( group.system ) "t" else "" )
  )
}
