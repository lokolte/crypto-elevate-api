package io.cryptoelevate.http

import cats.effect.ExitCode
import fs2.Stream.Compiler._
import io.cryptoelevate.http.Layers.AppEnv
import io.cryptoelevate.http.client.UserRoutesCaller
import io.cryptoelevate.http.routes.{ SystemRoutesService, UserRoutesService }
import io.cryptoelevate.Config
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.{ log, Logging }
import zio.{ ExitCode => ZExitCode, _ }
import zio.console.Console

object Main extends App {
  type HttpLayer[A] = RIO[AppEnv with Clock, A]

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ZExitCode] =
    UserRoutesCaller.makeHttpClient.map(x => UserRoutesCaller.makeProgram(x)).flatMap { layer =>
      val prog: ZIO[AppEnv with Clock with Logging with Console, Throwable, ZExitCode] =
        for {
          cfg <- Config.loadConfig()
          _   <- log.info(s"Starting on ${cfg.http.host}:${cfg.http.port}")
          httpApp = Router[HttpLayer](
            "/health" -> SystemRoutesService.routes(),
            "/api/v1/user" -> UserRoutesService.routes()
          ).orNotFound
          _ <- runHttp(httpApp, cfg.http.host, cfg.http.port)

        } yield ZExitCode.success

      prog
        .provideSomeLayer[ZEnv](Layers.appLayer ++ Clock.live ++ layer)
        .orDie
    }

  private def runHttp[R <: Clock](httpApp: HttpApp[RIO[R, *]], host: String, port: Int): ZIO[R, Throwable, Unit] = {
    type Task[A] = RIO[R, A]
    ZIO.runtime[R].flatMap { implicit rts =>
      BlazeServerBuilder
        .apply[Task](rts.platform.executor.asEC)
        .bindHttp(port, host)
        .withHttpApp(CORS(httpApp))
        .serve
        .compile[Task, Task, ExitCode]
        .drain
    }
  }
}
