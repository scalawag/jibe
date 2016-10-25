package org.scalawag.jibe.report

import java.io.{File, FileFilter}
import java.nio.file.{FileSystems, Path, WatchEvent}
import java.util.concurrent.atomic.AtomicReference
import org.scalawag.jibe.FileUtils._
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.Logging._
import scala.io.Source
import spray.json._
import JsonFormat._

object RunListWatcher {
  case class RunList(checksum: String, timestamp: Long, runs: Seq[Run])
}

class RunListWatcher(resultsDir: File) {
  import java.nio.file.StandardWatchEventKinds._
  import scala.collection.JavaConversions._
  import RunListWatcher.RunList

  private[this] val latest = new AtomicReference[RunList](null)

  private[this] val w = FileSystems.getDefault().newWatchService()

  private[this] def watch(files: File*) =
    files foreach { f =>
      // TODO: Uses unsupported com.sun package but otherwise has a ~10s delay on OS X.
      f.toPath.register(w, Array[WatchEvent.Kind[_]](ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), com.sun.nio.file.SensitivityWatchEventModifier.HIGH)
    }

  watch(resultsDir)

  private[this] val poller = {
    val t = new Thread() {
      override def run() = {
        try {
          while(true) {
            val key = w.take()

            val rebuild =
              key.pollEvents() exists { e =>
                if ( e.kind() == OVERFLOW || e.context() == resultsDir ) {
                  true
                } else {
                  // It's not the resultsDir that changed, see if it's a mandate.js or run.js
                  val name = e.context().asInstanceOf[Path].getFileName()
                  ( name == "mandate.js" || name == "run.js" )
                }
              }

            key.reset()

            val rl = buildRunList()

            // If the checksum hasn't changed, we don't need to do anything
            if ( rl.checksum != latest.get.checksum )
              latest.set(rl)
          }
        } catch {
          case ex: InterruptedException => // NOOP, just fall out of the loop
        }
      }
    }

    t.setDaemon(true)
    t.start()
  }

  private[this] def buildRunList(): RunList = {
    val dirFilter = new FileFilter {
      override def accept(f: File) = f.isDirectory
    }

    val runs = resultsDir.listFiles(dirFilter).sortBy(_.getName).reverse.flatMap { subdir =>
      try {
        val mandateFile = subdir / "mandate.js"
        val runFile = subdir / "run.js"
        if ( mandateFile.exists && runFile.exists ) {
          val run = Source.fromFile(runFile).mkString.parseJson.convertTo[Run]
          val mandate = Source.fromFile(mandateFile).mkString.parseJson.convertTo[MandateStatus]

          // Begin watching this directory for changes to the two new significant files.
          watch(subdir)

          Some(run.copy(mandate = Some(mandate)))
        } else {
          None
        }
      } catch {
        case ex: Exception =>
          log.warn(s"unable to treat directory $subdir as a valid run due to exception: $ex")
          None
      }
    }

    RunList(DigestUtils.md5Hex(runs.toString).toLowerCase, System.currentTimeMillis, runs)
  }

  // intialize our run list
  latest.set(buildRunList())

  def getRunList() = latest.get()
}
