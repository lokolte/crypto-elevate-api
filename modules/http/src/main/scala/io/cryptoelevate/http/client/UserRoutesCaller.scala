package io.cryptoelevate.http.client

import io.circe.{ Decoder, Json }
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import zio.interop.catz._
import zio.logging.Logging
import zio.{ Task, ZIO, ZLayer, _ }
import io.cryptoelevate.http.client.UserRoutesClient.UserRoutesClient
import zio._
import zio.clock.Clock
import zio.console.Console

import scala.concurrent.ExecutionContext.{ global, Implicits }

object UserRoutesCaller extends Http4sClientDsl[Task] {
  type UserRoutesCaller = Has[UserRoutesCaller.Service]

  trait Service {
    def postNewUser(body: Json)(implicit d: Decoder[String]): Task[String]
    def postUpdateWallet(body: Json)(implicit d: Decoder[String]): Task[String]
  }
  import scala.concurrent.ExecutionContext.global

  def makeHttpClient: UIO[TaskManaged[Client[Task]]] =
    ZIO.runtime[Any].map { implicit rts =>
      BlazeClientBuilder
        .apply[Task](Implicits.global)
        .resource
        .toManaged
    }
  def makeProgram(http4sClient: TaskManaged[Client[Task]]): ZLayer[Console with Clock, Nothing, UserRoutesClient] = {
    val loggerLayer = Logging
      .console()

    val httpClientLayer = http4sClient.toLayer.orDie
    val http4sClientLayer = (loggerLayer ++ httpClientLayer) >>> UserRoutesClient.live
    http4sClientLayer.orDie
  }

  val liveConnectorLayer: URLayer[UserRoutesClient with Logging, UserRoutesCaller] =
    (for {
      logLayer: Logging <- ZIO.identity[Logging]
      http4s            <- ZIO.service[UserRoutesClient.Service]
    } yield new UserRoutesCaller.Service with Http4sClientDsl[Task] {
      private val log = logLayer.get

      override def postNewUser(body: Json)(implicit d: Decoder[String]): Task[String] =
        http4s.post[String]("newUser", Map(), body)

      override def postUpdateWallet(body: Json)(implicit d: Decoder[String]): Task[String] =
        http4s.post[String]("updateWallet", Map(), body)
    }).toLayer

  def postUpdateWallet(body: Json)(implicit d: Decoder[String]): ZIO[UserRoutesCaller, Throwable, String] =
    ZIO.accessM(_.get.postUpdateWallet(body))

  def postNewUser(body: Json)(implicit d: Decoder[String]): ZIO[UserRoutesCaller, Throwable, String] =
    ZIO.accessM(_.get.postNewUser(body))
}
