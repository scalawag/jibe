package org.scalawag.jibe.backend

case class Command(mandate: Mandate, command: String) {
  def perform(ssh: SSHConnectionInfo) = {
    Sessions.get(ssh).execute(command, true)
  }
}
