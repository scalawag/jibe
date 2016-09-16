package org.scalawag.jibe.backend

import org.scalawag.jibe.mandate.Mandate

trait Commander {
  def getCommand(mandate: Mandate): Command
}
