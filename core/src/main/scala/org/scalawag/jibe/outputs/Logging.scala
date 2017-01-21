package org.scalawag.jibe.outputs

object Logging {
  import org.scalawag.timber.api.Level._
  import org.scalawag.timber.api.{ImmediateMessage, Logger}
  import org.scalawag.timber.backend.DefaultDispatcher
  import org.scalawag.timber.backend.dispatcher.Dispatcher
  import org.scalawag.timber.backend.dispatcher.configuration.dsl._
  import org.scalawag.timber.backend.receiver.ConsoleOutReceiver
  import org.scalawag.timber.backend.receiver.buffering.ImmediateFlushing
  import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter
  import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter.entry

  def initialize(): Unit = {
    implicit val MyEntryFormatter = new ProgrammableEntryFormatter(Seq(
      entry.timestamp
    ))

    val config = org.scalawag.timber.backend.dispatcher.configuration.Configuration {
      val out = new ConsoleOutReceiver(MyEntryFormatter) with ImmediateFlushing
      ( level >= DEBUG ) ~> out
    }

    val disp = new Dispatcher(config)
    DefaultDispatcher.set(disp)
  }

  val log = new Logger(tags = Set(ImmediateMessage))
}
