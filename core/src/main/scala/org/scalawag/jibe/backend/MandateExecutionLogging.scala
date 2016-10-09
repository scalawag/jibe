package org.scalawag.jibe.backend

import java.io.File
import org.scalawag.jibe.FileUtils._
import org.scalawag.timber.api.{Logger, Tag}

object MandateExecutionLogging {

  val CommandStart = Set(new Tag {
    override def toString = "CS"
  })

  val CommandContent = Set(new Tag {
    override def toString = "CC"
  })

  val CommandOutput = Set(new Tag {
    override def toString = "CO"
  })

  val CommandExit = Set(new Tag {
    override def toString = "CE"
  })

  val FunctionStart = Set(new Tag {
    override def toString = "FS"
  })

  val FunctionReturn = Set(new Tag {
    override def toString = "FR"
  })

  val ExceptionStackTrace = Set(new Tag {
    override def toString = "EE"
  })

  private[this] val singleLetterLevelFormatter = {
    import org.scalawag.timber.backend.receiver.formatter.level.TranslatingLevelFormatter
    import org.scalawag.timber.api.Level

    new TranslatingLevelFormatter(Iterable(
      Level(Level.DEBUG, "DBG"),
      Level(Level.INFO , "INF"),
      Level(Level.WARN , "WRN"),
      Level(Level.ERROR, "ERR")
    ))
  }

  private[this] val mandateEntryFormatter = {
    import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter
    import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter.entry
    import org.scalawag.timber.backend.receiver.formatter.timestamp.HumanReadableTimestampFormatter
    import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter.Commas

    new ProgrammableEntryFormatter(
      Seq(
        entry.tags formattedWith Commas,
        entry.level formattedWith singleLetterLevelFormatter,
        entry.timestamp formattedWith HumanReadableTimestampFormatter
      ),
      firstLinePrefix = "",
      continuationPrefix = "",
      continuationHeader = ProgrammableEntryFormatter.ContinuationHeader.METADATA
    )
  }

  def createMandateLogger(resultsDir: File) = {
    import org.scalawag.timber.backend.dispatcher.configuration.dsl._
    import org.scalawag.timber.backend.dispatcher.Dispatcher
    import org.scalawag.timber.backend.dispatcher.configuration.Configuration
    import org.scalawag.timber.backend.receiver.buffering.ImmediateFlushing
    import org.scalawag.timber.backend.receiver.concurrency.Queueing

    val dispatcher = new Dispatcher(Configuration {
      file((resultsDir / "log").getAbsolutePath, ImmediateFlushing, Queueing)(mandateEntryFormatter)
    })
    new Logger()(dispatcher)
  }

}
