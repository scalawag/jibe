package org.scalawag.jibe

import org.scalawag.timber.api.Logger

object TestLogging {
  private[this] implicit val dispatcher = {
    import org.scalawag.timber.backend.dispatcher.Dispatcher
    import org.scalawag.timber.backend.dispatcher.configuration.Configuration
    import org.scalawag.timber.backend.dispatcher.configuration.dsl._
    import org.scalawag.timber.backend.receiver.buffering.ImmediateFlushing

    new Dispatcher(Configuration(file("target/test.log", ImmediateFlushing)))
  }

  val log = new Logger()
}
