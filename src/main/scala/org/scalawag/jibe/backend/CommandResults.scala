package org.scalawag.jibe.backend

case class CommandResults(command: String, exitCode: Int, stdout: String, stderr: String)
