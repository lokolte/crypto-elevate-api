package io.cryptoelevate.http.client

import io.circe.Json
import org.http4s.{ Method, Request, Uri }
import zio.logging.Logging
import zio.{ Task, ZIO, ZLayer }
import io.circe.Decoder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.Client
import zio._
import zio.interop.catz._

object UserRoutesClient {
  type UserRoutesClient = Has[UserRoutesClient.Service]
  trait Service {

    def post[T](resource: String, parameters: Map[String, String], body: Json)(implicit d: Decoder[T]): Task[T]

  }

  val live: ZLayer[Logging with Has[Client[Task]], Throwable, UserRoutesClient] = (for {

    client: Client[Task] <- ZIO.service[Client[Task]]

    logLayer <- ZIO.identity[Logging]

  } yield new UserRoutesClient.Service {

    private val log = logLayer.get

    protected val rootUrl = "https://cue-qa-token.se.digital/api/cue/"

    def post[T](resource: String, parameters: Map[String, String], body: Json)(implicit d: Decoder[T]): Task[T] = {
      val uri = Uri(path = rootUrl + resource).withQueryParams(parameters)
      import org.http4s.circe._

      val req = Request[Task](method = Method.POST, uri = uri)
        .withEntity(body)

      log.info(s"GET REQUEST: $uri") *>
        client
          .expect[T](req.toString())
          .foldM(e => log.debug("Request failed" + e.toString) *> IO.fail(e), ZIO.succeed(_))
    }

  }).toLayer

}
