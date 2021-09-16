package io.cryptoelevate.dataservice

import io.cryptoelevate.connectors.models._
import io.cryptoelevate.connectors.interfaces.ses.SesConnector
import io.cryptoelevate.connectors.interfaces.cognito.CognitoConnector
import io.cryptoelevate.connectors.interfaces.lambda.LambdaConnector
import io.cryptoelevate.connectors.errors.{DynamoEmailNotFoundError, DynamoTZ1KeyNotExistError, _}
import io.cryptoelevate.connectors.models.connectors.{CognitoConnector, DynamoConnector, LambdaConnector, RedisConnector, SesConnector}

import java.util.UUID
import io.cryptoelevate.model._
import zio.{IO, _}

import java.util.UUID.randomUUID
import io.cryptoelevate.connectors.interfaces.dynamo.DynamoConnector
import zio._
import io.cryptoelevate.connectors.interfaces.redis.RedisConnector
object UserService {
  trait Service {
    def createNewUser(
      email: EmailAddress,
      password: Password,
      firstName: FirstName,
      lastName: LastName,
      nationalId: NationalId
    ): IO[UserError, User]

    def authUser(email: EmailAddress, password: Password): IO[UserError, CognitoUserTokens]
    def sendForgotPasswordEmail(email: EmailAddress): UIO[Unit]
    def confirmForgotPassword(email: EmailAddress, password: Password, code: String): IO[UserError, Unit]
    def getUserTz1Keys(accessToken: AccessToken): IO[UserError, UserTz1Keys]
    def getUserBurnedTz1Keys(accessToken: AccessToken): IO[UserError, List[UserTz1Keys]]
    def burnUserTz1Keys(emailAddress: EmailAddress, userTz1Keys: UserTz1Keys): IO[UserError, UserHistory]
    def moveUserTz1Keys(accessToken: AccessToken, publicKey: String, publicKeyHash: String): IO[UserError, UserHistory]
    def refreshToken(userId: UserId, refreshToken: String): IO[UserError, CognitoUserTokens]
    def sendEmail(accessToken: AccessToken, newTz1: UserTz1Keys): IO[UserError, UUID]
    def getRedis(uuid: UUID): IO[UserError, RedisUser]
  }

  val live: ZLayer[CognitoConnector with RedisConnector with DynamoConnector with SesConnector with LambdaConnector, Nothing, UserService] =
    (for {
      cognitoConnector <- ZIO.service[CognitoConnector.Service]
      redisConnector   <- ZIO.service[RedisConnector.Service]
      dynamoConnector  <- ZIO.service[DynamoConnector.Service]
      sesConnector     <- ZIO.service[SesConnector.Service]
      lambdaConnector  <- ZIO.service[LambdaConnector.Service]
    } yield new Service {

      override def createNewUser(
        email: EmailAddress,
        password: Password,
        firstName: FirstName,
        lastName: LastName,
        nationalId: NationalId
      ): IO[UserError, User] = for {
        _ <- dynamoConnector.createNewUser(email, None).mapError { //TODO Error Maping need to be rewrited
          case error @ (DynamoEmailExistsError | DynamoInvalidEmailNotUniqueError) =>
            UserError(msg = error.msg, cause = UserGenericError)
          case error: DynamoError => UserError(msg = error.msg, cause = UserServerError)
        }
        cognito <- cognitoConnector.createNewUser(email, password, firstName, lastName, nationalId).mapError {
          case error @ (CognitoInvalidPasswordNotSatisfyConstraintError | UsernameExistsError |
              CognitoInvalidNationalIdNotUniqueError) =>
            UserError(msg = error.msg, cause = UserGenericError)
          case error: CognitoError => UserError(msg = error.msg, cause = UserServerError)
        }
      } yield cognito

      override def authUser(email: EmailAddress, password: Password): IO[UserError, CognitoUserTokens] =
        cognitoConnector.authUser(email, password).mapError {
          case error @ CognitoInvalidUsernameOrPasswordError =>
            UserError(msg = error.msg, cause = UserNotAuthorized)
          case error @ (CognitoUserNotConfirmedError | CognitoPasswordResetRequiredError) =>
            UserError(msg = error.msg, cause = UserGenericError)
          case error: CognitoError => UserError(msg = error.msg, cause = UserServerError)
        }

      override def sendForgotPasswordEmail(email: EmailAddress): UIO[Unit] =
        cognitoConnector.sendForgotPasswordEmail(email).ignore

      override def confirmForgotPassword(
        email: EmailAddress,
        newPassword: Password,
        code: String
      ): IO[UserError, Unit] =
        cognitoConnector.confirmForgotPassword(email, newPassword, code).mapError {
          case error @ (CognitoInvalidPasswordNotSatisfyConstraintError |
              CognitoInvalidPasswordChangeValidationCodeError | CognitoForgotPasswordCodeExpiredError) =>
            UserError(msg = error.msg, cause = UserGenericError)
          case error: CognitoError => UserError(msg = error.msg, cause = UserServerError)
        }

      override def burnUserTz1Keys(emailAddress: EmailAddress, userTz1Keys: UserTz1Keys): IO[UserError, UserHistory] =
        for {
          oldUserHistory <- dynamoConnector.getUser(emailAddress).mapError {
            case error: DynamoError => UserError(msg = error.msg, cause = UserServerError)
          }
          userHistory <- dynamoConnector.burnTz1Keys(emailAddress, userTz1Keys).mapError {
            case error @ DynamoEmailNotFoundError  => UserError(msg = error.msg, cause = DynamoDbEmailNotFoundError)
            case error @ DynamoTZ1KeyNotExistError => UserError(msg = error.msg, cause = DynamoDbInvalidTz1Error)
            case error                             => UserError(msg = error.msg, cause = DynamoDbGenericError)
          }
          _ <- lambdaConnector.burn(oldUserHistory.actualTZ1Keys.getOrElse(UserTz1Keys("","")), userTz1Keys).mapError {
            case error @ LambdaServiceError => UserError(msg = error.msg, cause = LambdaInvocationServiceError)
            case error                      => UserError(msg = error.msg, cause = UserServerError)
          }
        } yield userHistory

      override def moveUserTz1Keys(
        accessToken: AccessToken,
        publicKey: String,
        publicKeyHash: String
      ): IO[UserError, UserHistory] = for {
        email <- cognitoConnector.getUserEmailByAccessToken(accessToken).mapError { error: CognitoError =>
          UserError(msg = error.msg, cause = UserGenericError)
        }
        tz1Key = UserTz1Keys(publicKey, publicKeyHash)
        userHistory <- dynamoConnector.moveUserTz1Key(email, tz1Key).mapError {
          case err @ DynamoEmailNotFoundError => UserError(msg = err.msg, cause = DynamoDbEmailNotFoundError)
          case err @ DynamoInvalidTZ1KeyError => UserError(msg = err.msg, cause = DynamoDbInvalidTz1Error)
          case err                            => UserError(msg = err.msg, cause = DynamoDbGenericError)
        }
        _ <- cognitoConnector.updateUserTz1Keys(accessToken, tz1Key).mapError { error: CognitoError =>
          UserError(msg = error.msg, cause = UserGenericError)
        }
      } yield userHistory

      override def getUserTz1Keys(accessToken: AccessToken): IO[UserError, UserTz1Keys] =
        cognitoConnector.getUserTz1Keys(accessToken).mapError {
          case error @ (CognitoInvalidPasswordNotSatisfyConstraintError |
              CognitoInvalidPasswordChangeValidationCodeError | CognitoForgotPasswordCodeExpiredError) =>
            UserError(msg = error.msg, cause = UserGenericError)
          case error: CognitoError => UserError(msg = error.msg, cause = UserServerError)
        }

      override def getUserBurnedTz1Keys(accessToken: AccessToken): IO[UserError, List[UserTz1Keys]] =
        for {
          email <- cognitoConnector.getUserEmailByAccessToken(accessToken).mapError { error: CognitoError =>
            UserError(msg = error.msg, cause = UserGenericError)
          }
          userHistory <- dynamoConnector.getUser(email).mapError { error: DynamoError =>
            UserError(msg = error.toString, cause = UserGenericError)
          }
        } yield userHistory.burnedTZ1Keys

      override def refreshToken(userId: UserId, refreshToken: String): IO[UserError, CognitoUserTokens] =
        cognitoConnector.refreshToken(userId, RefreshToken(refreshToken)).mapError {
          case error @ CognitoInvalidUsernameOrPasswordError =>
            UserError(msg = error.msg, cause = UserNotAuthorized)
          case error @ (CognitoUserNotConfirmedError | CognitoPasswordResetRequiredError) =>
            UserError(msg = error.msg, cause = UserGenericError)
          case error: CognitoError => UserError(msg = error.msg, cause = UserServerError)
        }

      override def sendEmail(accessToken: AccessToken, newTz1: UserTz1Keys): IO[UserError, UUID] =
        for { //TODO: change error handling in UserError
          email <- cognitoConnector.getUserEmailByAccessToken(accessToken) mapError { error =>
            UserError(msg = error.msg, cause = UserGenericError)
          }
          uuid = randomUUID()
          _ <- sesConnector.send(email.value, uuid.toString) mapError {
            case error @ SesUserNotAuthorisedToSendEmails =>
              UserError(msg = error.msg, cause = UserGenericError)
            case error =>
              UserError(msg = error.toString, cause = UserServerError)
          }

          _ <- redisConnector.set(RedisUser(uuid, email, newTz1)) mapError {
            case error @ RedisUserNotFound =>
              UserError(msg = error.msg, cause = UserGenericError)
            case error =>
              UserError(msg = error.toString, cause = UserServerError)
          }
        } yield uuid

      override def getRedis(uuid: UUID): IO[UserError, RedisUser] =
        for { //TODO: change error handling in UserError
          user <- redisConnector.get(uuid) mapError {
            case error @ RedisUserNotFound =>
              UserError(msg = error.msg, cause = UserGenericError)
            case error =>
              UserError(msg = error.toString, cause = UserServerError)
          }
        } yield user
    }).toLayer

  def createNewUser(
    email: EmailAddress,
    password: Password,
    firstName: FirstName,
    lastName: LastName,
    nationalId: NationalId
  ): ZIO[UserService, UserError, User] =
    ZIO.accessM(_.get.createNewUser(email, password, firstName, lastName, nationalId))

  def authUser(email: EmailAddress, password: Password): ZIO[UserService, UserError, CognitoUserTokens] =
    ZIO.accessM(_.get.authUser(email, password))

  def sendForgotPasswordEmail(email: EmailAddress): URIO[UserService, Unit] =
    ZIO.accessM(_.get.sendForgotPasswordEmail(email))

  def confirmForgotPassword(email: EmailAddress, password: Password, code: String): ZIO[UserService, UserError, Unit] =
    ZIO.accessM(_.get.confirmForgotPassword(email, password, code))

  def burnUserTz1Keys(emailAddress: EmailAddress, userTz1Keys: UserTz1Keys): ZIO[UserService, UserError, UserHistory] =
    ZIO.accessM(_.get.burnUserTz1Keys(emailAddress, userTz1Keys))

  def moveUserTz1Keys(
    accessToken: AccessToken,
    publicKey: String,
    publicKeyHash: String
  ): ZIO[UserService, UserError, UserHistory] =
    ZIO.accessM(_.get.moveUserTz1Keys(accessToken, publicKey, publicKeyHash))

  def getUserTz1Keys(accessToken: AccessToken): ZIO[UserService, UserError, UserTz1Keys] =
    ZIO.accessM(_.get.getUserTz1Keys(accessToken))

  def getUserBurnedTz1Keys(accessToken: AccessToken): ZIO[UserService, UserError, List[UserTz1Keys]] =
    ZIO.accessM(_.get.getUserBurnedTz1Keys(accessToken))

  def refreshToken(userId: UserId, refreshToken: String): ZIO[UserService, UserError, CognitoUserTokens] =
    ZIO.accessM(_.get.refreshToken(userId, refreshToken))

  def sendEmail(accessToken: AccessToken, newTz1: UserTz1Keys): ZIO[UserService, UserError, UUID] =
    ZIO.accessM(_.get.sendEmail(accessToken, newTz1))

  def getRedis(uuid: UUID): ZIO[UserService, UserError, RedisUser] =
    ZIO.accessM(_.get.getRedis(uuid))
}
