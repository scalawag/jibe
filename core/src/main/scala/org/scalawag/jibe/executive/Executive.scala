package org.scalawag.jibe.executive

class Executive {
  /*
    def toRawDot(pw: PrintWriter): Unit = {
      graph.toDot(pw)
    }

    private[this] val df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS-'UTC'")

    def execute(resultsDir: File, takeAction: Boolean)(implicit ec: ExecutionContext): Future[Unit] = {
      // Make sure this graph is valid before we create a report directory.

      validate()

      // Create the run directory and write in our metadata (including schema version for backward compatibility)

      val now = System.currentTimeMillis
      val id = df.format(now)
      val runDir = resultsDir / id

      // Create the report directories that will be used to store the results of this run.  Start at the to and
      // work our way down creating any reports that we haven't yet and connecting all the branch reports to their
      // children.

      var reportsById = Map.empty[(Commander, MultiTreeId), Report]

      def createMultiTreeReport(multiTree: MultiTree, commanderDir: File, commander: Commander): (MultiTreeId, Report) = {
        val id = multiTreeIdMap(commander).getId(multiTree)
        val key = (commander, id)
        reportsById.get(key).map( id -> _ ) getOrElse {
          val dir = commanderDir / id.toString

          val report =
            multiTree match {
              case leaf: MultiTreeLeaf =>
                writeFileWithPrintWriter(dir / "leaf.js") { pw =>
                  pw.println(LeafReportAttributes(id, leaf.name, leaf.mandate.toString).toJson.prettyPrint)
                }

                new LeafReport(dir)

              case branch: MultiTreeBranch =>

                val children = branch.contents.map(createMultiTreeReport(_, commanderDir, commander)).toList

                writeFileWithPrintWriter(dir / "branch.js") { pw =>
                  pw.println(BranchReportAttributes(id, branch.name, children.map(_._1)).toJson.prettyPrint)
                }

                // TODO: write immutables: MandateCollectionStatus(id, multiTree.name, children.map(_.status.id))
                new RollUpReport(dir, children.map(_._2))
            }

          reportsById += ( key -> report )
          ( id -> report )
        }
      }

      def createCommanderReport(commanderMultiTree: CommanderMultiTree, commanderDir: File) = {
        val id = multiTreeIdMap(commanderMultiTree.commander).getId(commanderMultiTree.multiTree)

        val rootReport = createMultiTreeReport(commanderMultiTree.multiTree, commanderDir, commanderMultiTree.commander)

        writeFileWithPrintWriter(commanderDir / "commander.js") { pw =>
          pw.println(CommanderReportAttributes(commanderMultiTree.commander.toString, rootReport._1).toJson.prettyPrint)
        }

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

        writeFileWithPrintWriter(runDir / "run.js") { pw =>
          pw.println(RunReportAttributes(1, now, id, takeAction).toJson.prettyPrint)
        }

        new RollUpReport(runDir, childReports.map(_._2))
      }

      val runReport = createRunReport(runDir, commanderMultiTrees)

      graph.run(RunContext(takeAction, reportsById))
    }

  //  def execute(resultsDir: File, commanderMultiTrees: Seq[CommanderMultiTree], takeAction: Boolean): Unit = {
  ////    val graph = buildGraph(commanderMultiTrees)
  //  }
  /*
    private[this] val df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS-'UTC'")

    def execute(resultsDir: File, commanderMandates: Seq[CommanderMultiTree], takeAction: Boolean): RunMandateReport = {

      val now = System.currentTimeMillis
      val id = df.format(now)
      val runDir = resultsDir / id
      val job = MandateJob(runDir, mandate, true)


      val graph = buildGraph(job)

      writeFileWithPrintWriter(new File("graph.dot")) { pw =>
        graph.dump(pw)
      }

      // Issue warnings about unmanaged resources.
      graph.vertices foreach {
        case p: graph.Barrier if p.upstreamCount == 0 =>
          println(s"WARNING: resource $p is required but not produced by any mandates")
        case _ => // NOOP
      }

    }
  */
  */
}
