package org.scalawag.jibe.mandate

case class CompositeMandate(override val description: Option[String], mandates: Seq[Mandate], fixedOrder: Boolean = false) extends Mandate {
  override def consequences = mandates.flatMap(_.consequences)
  override def prerequisites = ( mandates.flatMap(_.prerequisites).toSet -- consequences )
}

object CompositeMandate {
  def apply(mandates: Mandate*): CompositeMandate =
    new CompositeMandate(None, mandates, false)

  def apply(description: String, mandates: Mandate*): CompositeMandate =
    new CompositeMandate(Some(description), mandates, false)
}
