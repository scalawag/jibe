package org.scalawag.jibe

import org.scalawag.timber.backend.DefaultDispatcher
import org.scalawag.timber.backend.receiver.Receiver

object TestLogging {
  private[this] implicit val dispatcher = {
    import org.scalawag.timber.backend.dispatcher.Dispatcher
    import org.scalawag.timber.backend.dispatcher.configuration.Configuration
    import org.scalawag.timber.backend.dispatcher.configuration.dsl._
    import org.scalawag.timber.backend.receiver.buffering.ImmediateFlushing

    val out = file("target/test.log", ImmediateFlushing)
    Receiver.closeOnShutdown(out)
    new Dispatcher(Configuration(out))
  }

  DefaultDispatcher.set(dispatcher)
}
