package org.scalawag.jibe.multitree

import org.scalawag.jibe.ReadThroughCache

import scala.annotation.tailrec

// Assigns a unique ID to each distinct MultiTree recursively.  Distinctness is based on the instance equality.

class MultiTreeIdMap(root: MultiTree) {
  private[this] val (idsByTree, pathCountsById) = {
    var nextSerial = Map.empty[String, Int]

    def nextSerialFor(fingerprint: String): Int = {
      var serial = nextSerial.getOrElse(fingerprint, 0)
      nextSerial += ( fingerprint -> ( serial + 1 ) )
      serial
    }

    // Once we finish with the initialization inside this block, this becomes a val (and remains immutable).

    var idsByTree = new ReadThroughCache[MultiTree, MultiTreeId]({ mt =>
      val fingerprint = mt.fingerprint
      val serial = nextSerialFor(fingerprint)
      MultiTreeId(fingerprint, serial)
    })

    var treesById = Map.empty[MultiTreeId, Int]

    @tailrec
    def walkTree(trees: List[MultiTree]): Unit = trees match {

      case Nil =>
        // NOOP, stop recursing

      case multiTree :: rest =>
        val id = idsByTree.getOrCreate(multiTree)
        treesById += ( id -> ( treesById.getOrElse(id, 0) + 1 ) )

        multiTree match {
          case branch: MultiTreeBranch => walkTree(rest ++ branch.contents)
          case leaf: MultiTreeLeaf => walkTree(rest)
        }
    }

    walkTree(List(root))

    (idsByTree, treesById)
  }

  def getId(tree: MultiTree) = idsByTree(tree)
  def getPathCount(id: MultiTreeId) = pathCountsById(id)
}
