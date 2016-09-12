package org.scalawag.jibe.backend

case class CompositeMandate(override val description: Option[String], mandates: Mandate*) extends Mandate {
  override def prerequisites = mandates.flatMap(_.prerequisites)
  override def consequences = mandates.flatMap(_.consequences)
}

object CompositeMandate {
  def apply(mandates: Mandate*): CompositeMandate =
    new CompositeMandate(None, mandates:_*)

  def apply(description: String, mandates: Mandate*): CompositeMandate =
    new CompositeMandate(Some(description), mandates:_*)
}
