package org.scalawag.jibe.outputs

class CompositeMandate[-A, +B](val description: String, basis: Mandate[A, B]) extends Mandate[A, B] {
  override def bind(in: UpstreamBoundMandate[A])(implicit runContext: RunContext) = basis.bind(in)
  override val toString = description
}
