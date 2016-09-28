package org.scalawag.jibe.backend

case class CommandResults(testResults: ScriptResults, performResults: Option[ScriptResults] = None)

case class ScriptResults(command: String, exitCode: Int, stdout: String, stderr: String)
