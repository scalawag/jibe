package org.scalawag.jibe.backend

abstract class CompositeMandateBase(override val description: Option[String], val mandates: Seq[Mandate]) extends Mandate

// Child mandates can be executed in any order
case class MandateSet(override val description: Option[String], override val mandates: Seq[Mandate])
  extends CompositeMandateBase(description, mandates)

// Child mandates must be executed in order.
case class MandateSequence(override val description: Option[String], override val mandates: Seq[Mandate])
  extends CompositeMandateBase(description, mandates)

case class CommanderMandate(commander: Commander, val mandate: Mandate)
  extends CompositeMandateBase(Some(commander.toString), Seq(mandate))

case class RunMandate(val timestamp: String, override val mandates: Seq[CommanderMandate])
  extends CompositeMandateBase(Some(timestamp), mandates)
