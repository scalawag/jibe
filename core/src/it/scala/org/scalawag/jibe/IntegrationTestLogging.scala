package org.scalawag.jibe

import org.scalawag.timber.api.Logger
import org.scalawag.timber.backend.DefaultDispatcher

object IntegrationTestLogging {
  private[this] implicit val dispatcher = {
    import org.scalawag.timber.backend.dispatcher.Dispatcher
    import org.scalawag.timber.backend.dispatcher.configuration.Configuration
    import org.scalawag.timber.backend.receiver.buffering.ImmediateFlushing
    import org.scalawag.timber.backend.dispatcher.configuration.dsl._

    new Dispatcher(Configuration(file("target/it.log", ImmediateFlushing)))
  }

  DefaultDispatcher.set(dispatcher)

  val log = new Logger()
}
