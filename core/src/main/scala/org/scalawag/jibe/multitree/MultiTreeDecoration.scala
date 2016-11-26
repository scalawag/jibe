package org.scalawag.jibe.multitree

import org.scalawag.jibe.report.Report

// When the MultiTree is planned, Scope can be used to determine whether certain decorations target an object which
// spans commanders or not.

sealed trait Scope

// Global scope means that the resource spans commanders during the run (i.e., contention is across commanders).
// One resource is created for the entire run.

case object GlobalScope extends Scope

// Commander scope means that resource is contended only within a commander.  The same resource can be used
// concurrently by different commanders. One resource is created for each commander.

case object CommanderScope extends Scope

trait Scoped {
  val scope: Scope
}

sealed trait MultiTreeDecoration

case class Prerequisites(resources: Set[Resource]) extends MultiTreeDecoration

object Prerequisites {
  def apply(r: Resource): Prerequisites = new Prerequisites(Set(r))
  def apply(rs: Iterable[Resource]*): Prerequisites = new Prerequisites(rs.flatten.toSet)
}

case class Consequences(resources: Set[Resource]) extends MultiTreeDecoration

object Consequences {
  def apply(r: Resource): Consequences = new Consequences(Set(r))
  def apply(rs: Iterable[Resource]*): Consequences = new Consequences(rs.flatten.toSet)
}

case class Semaphore(val count: Int,
                     val name: Option[String] = None,
                     override val scope: Scope = CommanderScope) extends Scoped with OnlyIdentityEquals

case class EnterCriticalSection(semaphore: Semaphore) extends MultiTreeDecoration
case class ExitCriticalSection(semaphore: Semaphore) extends MultiTreeDecoration
case class CriticalSection(semaphore: Semaphore) extends MultiTreeDecoration

class Barrier(val name: Option[String] = None,
              override val scope: Scope = CommanderScope) extends Scoped

case class BeforeBarrier(barrier: Barrier) extends MultiTreeDecoration
case class AfterBarrier(barrier: Barrier) extends MultiTreeDecoration

sealed trait FlagStyle
case object ConjunctionFlag extends FlagStyle
case object DisjunctionFlag extends FlagStyle

class Flag(val name: Option[String] = None,
           val style: FlagStyle = DisjunctionFlag,
           override val scope: Scope = CommanderScope) extends Scoped {
  override def toString = s"Flag(${name.map(n => s""""$n"""").getOrElse(System.identityHashCode(this))})"
}

case class FlagOn(flag: Flag, status: Report.Status) extends MultiTreeDecoration
case class IfFlagged(flag: Flag) extends MultiTreeDecoration
case class IfUnflagged(flag: Flag) extends MultiTreeDecoration
