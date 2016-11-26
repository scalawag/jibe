package org.scalawag.jibe.multitree

import org.scalawag.jibe.MD5
import org.scalawag.jibe.multitree.MultiTreeBranch._

sealed trait MultiTree {
  type A <: MultiTree
  val label: Option[String]
  val decorations: Set[MultiTreeDecoration]

  val fingerprint: String

  def labelled(n: String): A
  def unlabelled: A
  def add(ds: MultiTreeDecoration*): A
  def remove(ds: MultiTreeDecoration*): A
  def unadorned: A
}

object MultiTree {
  implicit def treeify(m: Mandate) = MultiTreeLeaf(m)
}

case class MultiTreeLeaf(mandate: Mandate,
                         label: Option[String] = None,
                         decorations: Set[MultiTreeDecoration] = Set.empty) extends MultiTree
{
  override type A = MultiTreeLeaf

  override val fingerprint = mandate.fingerprint

  def labelled(n: String) = this.copy(label = Some(n))
  def unlabelled = this.copy(label = Some(mandate.label))
  def add(ds: MultiTreeDecoration*) = this.copy(decorations = this.decorations ++ ds)
  def remove(ds: MultiTreeDecoration*) = this.copy(decorations = this.decorations -- ds)
  def unadorned = this.copy(decorations = Set.empty)
}

object MultiTreeBranch {
  sealed trait Order
  case object Series extends Order
  case object Parallel extends Order
}

case class MultiTreeBranch(contents: Seq[MultiTree],
                           order: Order = Parallel,
                           label: Option[String] = None,
                           decorations: Set[MultiTreeDecoration] = Set.empty)
  extends MultiTree
{
  override type A = MultiTreeBranch

  override val fingerprint = MD5(contents.map(_.fingerprint).mkString(":"))

  def labelled(n: String): MultiTreeBranch = this.copy(label = Some(n))
  def unlabelled: MultiTreeBranch = this.copy(label = None)
  def add(ds: MultiTreeDecoration*) = this.copy(decorations = this.decorations ++ ds)
  def remove(ds: MultiTreeDecoration*) = this.copy(decorations = this.decorations -- ds)
  def unadorned = this.copy(decorations = Set.empty)
}

object MandateSet {
  def apply(contents: MultiTree*) =
    MultiTreeBranch(contents, Parallel)
  def apply(name: String, contents: MultiTree*) =
    MultiTreeBranch(contents, Parallel, Some(name))
}

object MandateSequence {
  def apply(contents: MultiTree*) =
    MultiTreeBranch(contents, Series)
  def apply(name: String, contents: MultiTree*) =
    MultiTreeBranch(contents, Series, Some(name))
}
