package org.scalawag.jibe.mandate

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.{Executive, Commander}

abstract class CompositeMandateBase[A <: Mandate](override val description: Option[String], mandates: Seq[A], fixedOrder: Boolean = false) extends Mandate {
  override def consequences = mandates.flatMap(_.consequences)

  override def prerequisites = (mandates.flatMap(_.prerequisites).toSet -- consequences)

  override def takeAction(implicit context: MandateExecutionContext): Unit =
    foreachMandateWithDirectory(context.resultsDir, unitReducer) { (mandate, subdir) =>
      Executive.executeMandate(Executive.TAKE_ACTION)(subdir, context.commander, mandate)
    }

  protected[this] val booleanReducer = { (l: Boolean, r: Boolean) => l || r }
  protected[this] val unitReducer = { (l: Unit, r: Unit) => () }

  protected[this] def foreachMandateWithDirectory[T](resultsDir: File, reducer: (T, T) => T)(fn: (A, File) => T): T = {
    val width = math.log10(mandates.length).toInt + 1

    mandates.zipWithIndex map { case (m, n) =>
      val subdir = s"%0${width}d".format(n + 1) + m.description.map(s => "_" + s.replaceAll("\\W+", "_")).getOrElse("")

      val childDir = mkdir(resultsDir / subdir)
      fn(m, childDir)
    } reduce(reducer)
  }
}

case class CompositeMandate(override val description: Option[String], mandates: Seq[Mandate], fixedOrder: Boolean = false)
  extends CompositeMandateBase[Mandate](description, mandates, fixedOrder)

case class CheckableCompositeMandate(override val description: Option[String], mandates: Seq[CheckableMandate], fixedOrder: Boolean = false)
  extends CompositeMandateBase[CheckableMandate](description, mandates, fixedOrder) with CheckableMandate {

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean =
    foreachMandateWithDirectory(context.resultsDir, booleanReducer) { (mandate, subdir) =>
      Executive.executeMandate(Executive.IS_ACTION_COMPLETED)(subdir, context.commander, mandate)
    }

  override def takeActionIfNeeded(implicit context: MandateExecutionContext): Boolean =
    foreachMandateWithDirectory(context.resultsDir, booleanReducer) { (mandate, subdir) =>
      Executive.executeMandate(Executive.TAKE_ACTION_IF_NEEDED)(subdir, context.commander, mandate)
    }
}

object CompositeMandate {
  def apply(mandates: Mandate*): CompositeMandate =
    new CompositeMandate(None, mandates, false)

  def apply(description: String, mandates: Mandate*): CompositeMandate =
    new CompositeMandate(Some(description), mandates, false)
}
