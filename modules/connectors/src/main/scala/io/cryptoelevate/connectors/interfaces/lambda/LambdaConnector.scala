package io.cryptoelevate.connectors.interfaces.lambda

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.lambda.{AWSLambda, AWSLambdaClientBuilder}
import com.amazonaws.services.lambda.model.ServiceException
import io.cryptoelevate.connectors.errors._
import io.cryptoelevate.connectors.models.connectors.{LambdaConnector, LambdaSdkCaller}
import io.cryptoelevate.connectors.models.{LambdaResponse, UserTz1Keys}
import io.cryptoelevate.{Config, Logger}
import zio.blocking.Blocking
import zio.logging.Logging
import zio.{Has, IO, RLayer, Task, TaskLayer, ZIO, ZLayer}

object LambdaConnector {
  trait Service {
    def burn(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): IO[LambdaError, LambdaResponse]
  }

  private val configuredLambdaClient: RLayer[Config, Has[AWSLambda]] =
    (for {
      config                    <- ZIO.accessM[Config](_.get.loadConfig())
      awsConfig                 <- Task.effectTotal(config.aws)
      keys = awsConfig.awsData
      id = keys.id
      secret = keys.secret
      region = awsConfig.region
      credentials               <- Task.effect(new BasicAWSCredentials(id, secret))
      credentialsProvider       <- Task.effect(new AWSStaticCredentialsProvider(credentials))
      client <- Task.effect(
        AWSLambdaClientBuilder
          .standard()
          .withCredentials(credentialsProvider)
          .withRegion(region)
          .build()
      )
    } yield client).toLayer.orDie

  lazy val live: TaskLayer[LambdaConnector] =
    ((Blocking.live ++ Config.live ++ (Config.live >>> configuredLambdaClient)) >>> LambdaSdkCaller.live) ++
      Logger.liveEnv("aws-lambda") >>> liveConnectorLayer

  private def errorMapping: PartialFunction[Throwable, LambdaError] = {
    case error =>
      LambdaGenericError(error.toString)
  }

  private[connectors] val liveConnectorLayer: ZLayer[LambdaSdkCaller with Logging, Nothing, LambdaConnector] =
    (for {
      sdkCaller <- ZIO.service[LambdaSdkCaller.Service]
      logLayer  <- ZIO.identity[Logging]
    } yield new Service {
      private val log = logLayer.get

      override def burn(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): IO[LambdaError, LambdaResponse] = sdkCaller
        .burn(oldAddress, newAddress)
        .foldM(
          error =>
            log.throwable(s"Error calling lambda: ", error) *>
            IO.fail(error match {
              case err: IllegalArgumentException => LambdaGenericError(err.toString)
              case _: ServiceException           => LambdaServiceError
              case err                           => errorMapping(err)
            }),
          response => IO.succeed(response)
        )
    }).toLayer

  def burn(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): ZIO[LambdaConnector, LambdaError, LambdaResponse] =
    ZIO.accessM(_.get.burn(oldAddress, newAddress))
}
