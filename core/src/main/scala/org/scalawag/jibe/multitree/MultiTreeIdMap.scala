package org.scalawag.jibe.multitree

import scala.annotation.tailrec

// Assigns a unique ID to each "distinct" MultiTree recursively.  Distinctness is based on the Object.equals method.
// If you want to use the same Mandate with the same arguments more than once within the same run, you need to ensure
// that they are not equal.  Refer to OnlyIdentityEquals.

class MultiTreeIdMap(root: MultiTree) {
  private[this] val idsByTree = {
    var nextSerial = Map.empty[String, Int]

    def nextSerialFor(fingerprint: String): Int = {
      var serial = nextSerial.getOrElse(fingerprint, 0)
      nextSerial += ( fingerprint -> ( serial + 1 ) )
      serial
    }

    // Once we finish with the initialization inside this block, this becomes a val (and remains immutable).

    var idsByTree = Map.empty[MultiTree, MultiTreeId]

    @tailrec
    def walkTree(trees: List[MultiTree]): Unit = trees match {

      case Nil =>
        // NOOP, stop recursing

      case multiTree :: rest =>
        if ( ! idsByTree.contains(multiTree) ) {
          val fingerprint = multiTree.fingerprint
          val serial = nextSerialFor(fingerprint)
          val id = MultiTreeId(fingerprint, serial)
          idsByTree += ( multiTree -> id )
        }

        multiTree match {
          case branch: MultiTreeBranch => walkTree(rest ++ branch.contents)
          case leaf: MultiTreeLeaf => walkTree(rest)
        }
    }

    walkTree(List(root))

    idsByTree
  }

  def getId(tree: MultiTree) = idsByTree(tree)
}
