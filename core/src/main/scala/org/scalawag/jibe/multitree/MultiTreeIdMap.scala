package org.scalawag.jibe.multitree

import scala.annotation.tailrec

// Assigns a unique ID to each distinct MultiTree recursively.  Note that "distinct" depends on the setting of
// Mandate.allowMultipleDistinctEqualInstancesPerRun for each Mandate involved.  For those, multiple equal
// instances can appear as distinct.

class MultiTreeIdMap(root: MultiTree) {
  private[this] val (distinctTreeMap, nonDistinctTreeMap, idMap) = {
    var nextSerial = Map.empty[String, Int]

    def nextSerialFor(fingerprint: String): Int = {
      var serial = nextSerial.getOrElse(fingerprint, 0)
      nextSerial += ( fingerprint -> ( serial + 1 ) )
      serial
    }

    // These are vars for the three maps that we're going to return.  Once we finish with the initialization
    // inside this block, they're vals and immutable.

    var distinctTreeMap = Map.empty[Int, MultiTreeId]
    var nonDistinctTreeMap = Map.empty[String, MultiTreeId]
    var idMap = Map.empty[MultiTreeId, MultiTree]

    @tailrec
    def walkTree(trees: List[MultiTree]): Unit = trees match {

      case Nil =>
        // NOOP, stop recursing

      case (branch: MultiTreeBranch) :: rest =>
        val key = branch.fingerprint
        if ( ! nonDistinctTreeMap.contains(key) ) {
          val fingerprint = branch.fingerprint
          val serial = nextSerialFor(fingerprint)
          val id = MultiTreeId(fingerprint, serial)
          nonDistinctTreeMap += ( key -> id )
          idMap += ( id -> branch )
        }
        walkTree(rest ++ branch.contents)

      case (leaf: MultiTreeLeaf) :: rest if leaf.mandate.allowMultipleDistinctEqualInstancesPerRun =>
        val key = System.identityHashCode(leaf.mandate)
        if ( ! distinctTreeMap.contains(key) ) {
          val fingerprint = leaf.fingerprint
          val serial = nextSerialFor(fingerprint)
          val id = MultiTreeId(fingerprint, serial)
          distinctTreeMap += ( key -> id )
          idMap += ( id -> leaf )
        }
        walkTree(rest)

      case (leaf: MultiTreeLeaf) :: rest =>
        val key = leaf.fingerprint
        if ( ! nonDistinctTreeMap.contains(key) ) {
          val fingerprint = leaf.fingerprint
          val serial = nextSerialFor(fingerprint)
          val id = MultiTreeId(fingerprint, serial)
          nonDistinctTreeMap += ( key -> id )
          idMap += ( id -> leaf )
        }
        walkTree(rest)
    }

    walkTree(List(root))

    (distinctTreeMap, nonDistinctTreeMap, idMap)
  }

  def all = idMap

  def getId(tree: MultiTree) = tree match {
    case b: MultiTreeBranch =>
      nonDistinctTreeMap(b.fingerprint)
    case l: MultiTreeLeaf if l.mandate.allowMultipleDistinctEqualInstancesPerRun =>
      distinctTreeMap(System.identityHashCode(l.mandate))
    case l: MultiTreeLeaf =>
      nonDistinctTreeMap(l.fingerprint)
  }
}
