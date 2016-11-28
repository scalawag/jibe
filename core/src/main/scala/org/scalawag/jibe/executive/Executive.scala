package org.scalawag.jibe.executive

import java.io.File
import java.text.SimpleDateFormat

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.{Commander, MandateExecutionLogging}
import org.scalawag.jibe.executive.PlanGraphFactory.{LeafVertex, LoggerFactory, VisitContext, VisitListener2}
import org.scalawag.jibe.multitree.{MultiTree, MultiTreeBranch, MultiTreeId, MultiTreeLeaf}
import org.scalawag.jibe.report.Report._
import org.scalawag.jibe.report.{LeafReport, Report, RollUpReport}
import org.scalawag.jibe.report.JsonFormats._

import scala.concurrent.{ExecutionContext, Future}

object Executive {
  private[this] val df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS-'UTC'")

  def execute(plan: ExecutionPlan, resultsDir: File, takeAction: Boolean)(implicit ec: ExecutionContext): Future[File] = {

    // Make sure this plan is valid before we even create a report directory.

    plan.validate()

    // Create the run directory and write in our metadata (including schema version for backward compatibility)

    val now = System.currentTimeMillis
    val id = df.format(now)
    val runDir = resultsDir / id

    // Create the report directories that will be used to store the results of this run.  Start at the to and
    // work our way down creating any reports that we haven't yet and connecting all the branch reports to their
    // children.

    var reportsById = Map.empty[(Commander, MultiTreeId), Report]

    def createMultiTreeReport(multiTree: MultiTree, commanderDir: File, commander: Commander): (MultiTreeId, Report) = {
      val id = plan.multiTreeIdMap(commander).getId(multiTree)
      val pathCount = plan.multiTreeIdMap(commander).getPathCount(id)

      val key = (commander, id)
      reportsById.get(key).map( id -> _ ) getOrElse {
        val dir = commanderDir / id.toString

        val report =
          multiTree match {
            case leaf: MultiTreeLeaf =>
              writeJson(dir / "leaf.js", LeafReportAttributes(id, pathCount, leaf.label, leaf.mandate.toString))

              new LeafReport(dir)

            case branch: MultiTreeBranch =>

              val children = branch.contents.map(createMultiTreeReport(_, commanderDir, commander)).toList
              writeJson(dir / "branch.js", BranchReportAttributes(id, pathCount, branch.label, children.map(_._1)))

              // TODO: write immutables: MandateCollectionStatus(id, multiTree.name, children.map(_.status.id))
              new RollUpReport(dir, children.map(_._2))
          }

        reportsById += ( key -> report )
        ( id -> report )
      }
    }

    def createCommanderReport(commanderMultiTree: CommanderMultiTree, commanderDir: File) = {
      val id = plan.multiTreeIdMap(commanderMultiTree.commander).getId(commanderMultiTree.multiTree)

      val rootReport = createMultiTreeReport(commanderMultiTree.multiTree, commanderDir, commanderMultiTree.commander)

      writeJson(commanderDir / "commander.js", CommanderReportAttributes(commanderMultiTree.commander.toString, rootReport._1))

      rootReport
    }

    def createRunReport(runDir: File, commanderMultiTrees: Seq[CommanderMultiTree]) = {
      val width = math.log10(commanderMultiTrees.size).toInt + 1
      val childReports =
        commanderMultiTrees.zipWithIndex map { case (cmt, n) =>
          val CommanderMultiTree(c, mt) = cmt
          val subdir = s"%0${width}d".format(n) + "_" + c.toString.replaceAll("\\W+", "_")
          createCommanderReport(cmt, runDir / subdir)
        }

      writeJson(runDir / "run.js", RunReportAttributes(1, now, id, takeAction))

      new RollUpReport(runDir, childReports.map(_._2))
    }

    val runReport = createRunReport(runDir, plan.commanderMultiTrees)

    def getReport(leafVertex: LeafVertex) = {
      val id = plan.multiTreeIdMap(leafVertex.commander).getId(leafVertex.leaf)
      reportsById(leafVertex.commander, id)
    }

    val visitListener = new VisitListener2 {
      override def enter(vertex: LeafVertex, status: Status) =
        getReport(vertex).status.mutate(_.copy(startTime = Some(System.currentTimeMillis), status = status, leafStatusCounts = Map(status -> 1)))

      override def bypass(vertex: LeafVertex, status: Status) =
        getReport(vertex).status.mutate(_.copy(status = status, leafStatusCounts = Map(status -> 1)))

      override def exit(vertex: LeafVertex, status: Status) =
        getReport(vertex).status.mutate(_.copy(endTime = Some(System.currentTimeMillis), status = status, leafStatusCounts = Map(status -> 1)))
    }

    val loggerFactory = new LoggerFactory {
      override def getLogger(vertex: LeafVertex) = MandateExecutionLogging.createMandateLogger(getReport(vertex).dir)
    }

    plan.runnableGraph.run(VisitContext(takeAction, visitListener, loggerFactory)) map { _ =>
      runDir
    }
  }
}
