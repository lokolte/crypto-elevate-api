package io.cryptoelevate.connectors.interfaces.dynamo

import io.cryptoelevate.connectors.errors.{DynamoEmailNotFoundError, DynamoError, DynamoGenericError, DynamoInvalidTZ1KeyError, DynamoTZ1KeyNotExistError, GenericError}
import io.cryptoelevate.connectors.models.connectors.{DynamoConnector, DynamoSdkCaller}
import io.cryptoelevate.connectors.models.{UserHistory, UserTz1Keys}
import io.cryptoelevate.model.EmailAddress
import io.cryptoelevate.{Config, Logger}
import zio.blocking.Blocking
import zio.logging.Logging
import zio.{IO, TaskLayer, ZIO, ZLayer}
object DynamoConnector {

  trait Service {
    def moveUserTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): IO[DynamoError, UserHistory]
    def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): IO[DynamoError, UserHistory]

    def createNewUser(
      email: EmailAddress,
      actualTZ1Key: Option[UserTz1Keys],
      burnedTZ1Keys: List[UserTz1Keys] = List()
    ): IO[DynamoError, Unit]

    def updateUserTz1Keys(email: EmailAddress, keys: List[UserTz1Keys]): IO[DynamoError, Unit]

    def getUser(email: EmailAddress): IO[DynamoError, UserHistory]
  }

  lazy val live: TaskLayer[DynamoConnector] =
    (Blocking.live ++ Config.live) >>>
      (DynamoSdkCaller.live ++ Logger.liveEnv("aws-dynamodb")) >>>
      liveConnectorLayer

  private def errorMapping: PartialFunction[Throwable, DynamoError] = { //TODO: Fix error messages
    case error =>
      DynamoGenericError(error.toString)
  }

  private[connectors] val liveConnectorLayer: ZLayer[DynamoSdkCaller with Logging, Nothing, DynamoConnector] =
    (for {
      sdkCaller <- ZIO.service[DynamoSdkCaller.Service]
      logLayer  <- ZIO.identity[Logging]
    } yield new Service {
      private val log = logLayer.get

      override def moveUserTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): IO[DynamoError, UserHistory] =
        sdkCaller
          .moveTz1Key(email, tz1Key = tz1Key)
          .foldM(
            error =>
              IO.fail(error match {
                case _: NoSuchElementException => DynamoEmailNotFoundError
                case _: IllegalArgumentException => DynamoInvalidTZ1KeyError
                case err => DynamoGenericError(err.toString)
              }),
            user => IO.succeed(user)
          )

      override def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): IO[DynamoError, UserHistory] = sdkCaller
        .burnTz1Keys(email, tz1Key)
        .foldM(
          error =>
            IO.fail(error match {
              case _: NoSuchElementException   => DynamoEmailNotFoundError
              case _: IllegalArgumentException => DynamoTZ1KeyNotExistError
              case err                         => DynamoGenericError(err.toString)
            }),
          user => IO.succeed(user)
        )

      override def createNewUser(
        email: EmailAddress,
        actualTZ1Key: Option[UserTz1Keys],
        burnedTZ1Keys: List[UserTz1Keys] = List()
      ): IO[DynamoError, Unit] =
        sdkCaller
          .createNewUser(email, actualTZ1Key, burnedTZ1Keys)
          .foldM(
            err =>
              log.throwable(s"Error while creating a new user email: ${email.value} ", err) *> IO
                .fail(errorMapping(err)),
            user => IO.succeed(user)
          )

      override def updateUserTz1Keys(email: EmailAddress, keys: List[UserTz1Keys]): IO[DynamoError, Unit] =
        sdkCaller
          .updateUserKeys(email, burnedTZ1Keys = keys)
          .foldM(
            err =>
              log.throwable(s"Error while udating data from user email: ${email.value} ", err) *> IO
                .fail(errorMapping(err)),
            user => IO.succeed(user)
          )

      override def getUser(email: EmailAddress): IO[DynamoError, UserHistory] = sdkCaller
        .getUserTz1Keys(email)
        .foldM(
          err =>
            log.throwable(s"Error while getting an user by email: ${email.value} ", err) *> IO.fail(errorMapping(err)),
          user => IO.succeed(user.get)
        )

    }).toLayer
  def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): ZIO[DynamoConnector, DynamoError, UserHistory] =
    ZIO.accessM(_.get.burnTz1Keys(email, tz1Key))

  def createNewUser(
    email: EmailAddress,
    actualTZ1Key: Option[UserTz1Keys],
    burnedTZ1Keys: List[UserTz1Keys] = List()
  ): ZIO[DynamoConnector, DynamoError, Unit] =
    ZIO.accessM(_.get.createNewUser(email, actualTZ1Key, burnedTZ1Keys))

  def updateUserTz1Keys(email: EmailAddress, keys: List[UserTz1Keys]): ZIO[DynamoConnector, DynamoError, Unit] =
    ZIO.accessM(_.get.updateUserTz1Keys(email, keys))

  def moveUserTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): ZIO[DynamoConnector, DynamoError, UserHistory] =
    ZIO.accessM(_.get.moveUserTz1Key(email, tz1Key))

  def getUserTz1Keys(email: EmailAddress): ZIO[DynamoConnector, DynamoError, UserHistory] =
    ZIO.accessM(_.get.getUser(email))

}
