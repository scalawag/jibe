package org.scalawag.jibe.backend

/** A Mandate is an operation that can be executed against a system.  It may be realized as a series of system-specific
  * commands or it can be an aggregation of several other Mandates.
  */

trait Mandate extends ResourceRelated

object Mandate {
  implicit class MandatePimper(mandate: Mandate) {
    def before(after: Mandate) = CompositeMandate(mandate, after)
  }
}
