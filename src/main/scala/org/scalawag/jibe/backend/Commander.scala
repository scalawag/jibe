package org.scalawag.jibe.backend

trait Commander {
  def getCommand(mandate: Mandate): Command
}
