package org.scalawag.jibe.multitree

trait MultiTree {
  type A <: MultiTree
  val name: Option[String]
  val decorations: Set[MultiTreeDecoration]

  val fingerprint: String

  def named(n: String): A
  def unnamed: A
  def add(ds: MultiTreeDecoration*): A
  def remove(ds: MultiTreeDecoration*): A
  def unadorned: A
}
