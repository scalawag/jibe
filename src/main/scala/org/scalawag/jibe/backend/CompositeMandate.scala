package org.scalawag.jibe.backend

case class CompositeMandate(mandates: Mandate*) extends Mandate {
  override def prerequisites = mandates.flatMap(_.prerequisites)
  override def consequences = mandates.flatMap(_.consequences)
}
