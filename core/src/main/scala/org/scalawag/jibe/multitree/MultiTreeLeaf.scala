package org.scalawag.jibe.multitree

case class MultiTreeLeaf(mandate: Mandate,
                         name: Option[String] = None,
                         decorations: Set[MultiTreeDecoration] = Set.empty) extends MultiTree
{
  override type A = MultiTreeLeaf

  override val fingerprint = mandate.mandateFingerprint

  def named(n: String) = this.copy(name = Some(n))
  def unnamed = this.copy(name = None)
  def add(ds: MultiTreeDecoration*) = this.copy(decorations = this.decorations ++ ds)
  def remove(ds: MultiTreeDecoration*) = this.copy(decorations = this.decorations -- ds)
  def unadorned = this.copy(decorations = Set.empty)
}
