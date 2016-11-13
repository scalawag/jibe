package org.scalawag.jibe

class ReadThroughCache[K, V](createFn: K => V) {
  private[this] var map = Map.empty[K, V]

  def getOrCreate(key: K): V = synchronized {
    map.get(key) getOrElse {
      val value = createFn(key)
      map += ( key -> value )
      value
    }
  }
}
