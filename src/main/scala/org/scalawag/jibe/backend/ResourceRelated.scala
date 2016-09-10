package org.scalawag.jibe.backend

trait ResourceRelated {
  def prerequisites: Iterable[Resource] = Iterable.empty
  def consequences: Iterable[Resource] = Iterable.empty
}
