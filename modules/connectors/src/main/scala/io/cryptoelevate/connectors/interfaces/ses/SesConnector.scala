package io.cryptoelevate.connectors.interfaces.ses

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.services.cognitoidp.model.{
  InvalidParameterException,
  InvalidPasswordException,
  UsernameExistsException
}
import com.amazonaws.services.simpleemail.model.{ AmazonSimpleEmailServiceException, SendEmailResult }
import com.amazonaws.services.simpleemail.{ AmazonSimpleEmailServiceAsync, AmazonSimpleEmailServiceAsyncClientBuilder }
import io.cryptoelevate.connectors.errors.{
  CognitoInvalidPasswordNotSatisfyConstraintError,
  GenericError,
  RedisError,
  SesConnectionError,
  SesError,
  UsernameExistsError
}
import io.cryptoelevate.connectors.models.connectors.{ SesConnector, SesSdkCaller }
import io.cryptoelevate.connectors.models.{ Address, Content, SendingEmail }
import io.cryptoelevate.{ Config, Logger }
import zio.blocking.Blocking
import zio.logging.Logging
import zio._

object SesConnector {

  trait Service {
    def send(email: String, uuid: String): IO[SesError, SendEmailResult]
  }
  private val configedAmazonSimpleEmailServiceAsync: RLayer[Config, Has[AmazonSimpleEmailServiceAsync]] =
    (for {
      config    <- ZIO.accessM[Config](_.get.loadConfig())
      awsConfig <- Task.effectTotal(config.aws)
      aws = awsConfig.awsData
      id = aws.id
      secret = aws.secret
      region = awsConfig.region
      credentials: BasicAWSCredentials <- Task.effect(new BasicAWSCredentials(id, secret))

      credentialsProvider <- Task.effect(new AWSStaticCredentialsProvider(credentials))
      client: AmazonSimpleEmailServiceAsync = AmazonSimpleEmailServiceAsyncClientBuilder
        .standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build()

    } yield client).toLayer.orDie

  lazy val live: TaskLayer[SesConnector] =
    ((Blocking.live ++ (Config.live >>> configedAmazonSimpleEmailServiceAsync)) >>> SesSdkCaller.live ++ Logger
      .liveEnv("aws-dynamodb") ++ Config.live) >>> liveConnectorLayer

  private def errorMapping: PartialFunction[Throwable, SesError] = {
    case e: AmazonSimpleEmailServiceException if e.getMessage.contains("is not authorized to perform") =>
      SesConnectionError
    case e: java.net.ConnectException =>
      SesConnectionError
    case error =>
      GenericError(error.toString)
  }

  private[connectors] val liveConnectorLayer: ZLayer[SesSdkCaller with Logging with Config, Throwable, SesConnector] =
    (for {
      config    <- ZIO.accessM[Config](_.get.loadConfig())
      sdkCaller <- ZIO.service[SesSdkCaller.Service]
      logLayer  <- ZIO.identity[Logging]
      awsConfig <- Task.effectTotal(config.aws.ses)
      url = awsConfig.url
      sendingEmailAddress = awsConfig.sendingEmailAddress
    } yield new Service {
      private val log = logLayer.get

      override def send(email: String, uuid: String): IO[SesError, SendEmailResult] = {
        val message = url + "/" + uuid
        val sendEmail =
          SendingEmail(
            Content(message),
            Address(sendingEmailAddress),
            to = Seq(Address(email)),
            bodyText = Some(Content(message))
          )
        sdkCaller.send(sendEmail).tapError(error => log.error("Ses occur error: " + error))
      }.mapError(errorMapping)

    }).toLayer

  def send(email: String, uuid: String): ZIO[SesConnector, SesError, SendEmailResult] =
    ZIO.accessM(_.get.send(email, uuid))

}
