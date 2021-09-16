package io.cryptoelevate

import zio.clock.Clock
import zio.console.Console
import zio.logging._
import zio.{ ULayer, ZLayer }

object Logger {

  private def env(rootLoggerName: String): ZLayer[Console with Clock, Nothing, Logging] =
    Logging.console(logLevel = LogLevel.Info, format = LogFormat.ColoredLogFormat()) >>> Logging.withRootLoggerName(
      rootLoggerName
    )

  def liveEnv(rootLoggerName: String): ULayer[Logging] =
    (Console.live ++ Clock.live) >>> env(rootLoggerName)

}
