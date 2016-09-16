package org.scalawag.jibe.mandate

import org.scalawag.jibe.backend.Resource

/** A Mandate is an operation that can be executed against a system.  It may be realized as a series of system-specific
  * commands or it can be an aggregation of several other Mandates.
  */

trait Mandate {
  val description: Option[String] = None
  def prerequisites: Iterable[Resource] = Iterable.empty
  def consequences: Iterable[Resource] = Iterable.empty
}

object Mandate {
  implicit class MandatePimper(mandate: Mandate) {
    def before(after: Mandate) = CompositeMandate("before", mandate, after)
  }
}
