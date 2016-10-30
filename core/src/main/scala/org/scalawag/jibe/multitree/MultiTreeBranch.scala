package org.scalawag.jibe.multitree

import org.scalawag.jibe.MD5
import MultiTreeBranch._

object MultiTreeBranch {
  sealed trait Order
  case object Series extends Order
  case object Parallel extends Order
}

case class MultiTreeBranch(contents: Seq[MultiTree],
                           order: Order = Parallel,
                           name: Option[String] = None,
                           decorations: Set[MultiTreeDecoration] = Set.empty)
  extends MultiTree
{
  override type A = MultiTreeBranch

  override val fingerprint = MD5(contents.map(_.fingerprint).mkString(":"))

  def named(n: String): MultiTreeBranch = this.copy(name = Some(n))
  def unnamed: MultiTreeBranch = this.copy(name = None)
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
