package io.cryptoelevate.connectors.interfaces.cognito

import com.amazonaws.services.cognitoidp.model._
import io.cryptoelevate.connectors.errors._
import io.cryptoelevate.connectors.models.connectors.{ CognitoConnector, CognitoSdkCaller }
import io.cryptoelevate.connectors.models.{ AccessToken, CognitoUserTokens, RefreshToken, UserTz1Keys }
import io.cryptoelevate.model._
import io.cryptoelevate.{ Config, Logger }
import zio.logging.Logging
import zio.{ IO, TaskLayer, ZIO, ZLayer }

object CognitoConnector {

  trait Service {
    def createNewUser(
      email: EmailAddress,
      password: Password,
      firstName: FirstName,
      lastName: LastName,
      nationalId: NationalId
    ): IO[CognitoError, User]
    def authUser(email: EmailAddress, password: Password): IO[CognitoError, CognitoUserTokens]
    def sendForgotPasswordEmail(email: EmailAddress): IO[CognitoError, Unit]
    def confirmForgotPassword(email: EmailAddress, password: Password, code: String): IO[CognitoError, Unit]
    def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): IO[CognitoError, Unit]
    def getUserTz1Keys(accessToken: AccessToken): IO[CognitoError, UserTz1Keys]
    def getUserEmailByAccessToken(accessToken: AccessToken): IO[CognitoError, EmailAddress]
    def refreshToken(userId: UserId, refreshToken: RefreshToken): IO[CognitoError, CognitoUserTokens]
  }

  lazy val live: TaskLayer[CognitoConnector] =
    Logger.liveEnv(
      "aws-cognito"
    ) ++ (Config.live ++ (Config.live >>> CognitoHashCalculator.live) >>> CognitoSdkCaller.live) >>> liveConnectorLayer

  private[connectors] val liveConnectorLayer: ZLayer[CognitoSdkCaller with Logging, Throwable, CognitoConnector] =
    (for {
      sdkCaller <- ZIO.service[CognitoSdkCaller.Service]
      logLayer  <- ZIO.identity[Logging]
    } yield new Service {

      private val log = logLayer.get

      override def createNewUser(
        email: EmailAddress,
        password: Password,
        firstName: FirstName,
        lastName: LastName,
        nationalId: NationalId
      ): IO[CognitoError, User] =
        sdkCaller
          .createNewUser(email, password, firstName, lastName, nationalId)
          .foldM(
            err =>
              log.throwable(s"Error while creating a new user email: ${email.value} ", err) *> IO.fail(err match {
                case _: InvalidPasswordException => CognitoInvalidPasswordNotSatisfyConstraintError
                case _: UsernameExistsException  => UsernameExistsError
                case e: InvalidParameterException if e.getMessage.contains("temporaryPassword") =>
                  CognitoInvalidPasswordNotSatisfyConstraintError
                case e: InvalidParameterException if e.getMessage.contains("NationalId is already taken") =>
                  CognitoInvalidNationalIdNotUniqueError
                case e: Exception =>
                  GenericError(e.toString)
              }),
            user => IO.succeed(user)
          )

      override def authUser(email: EmailAddress, password: Password): IO[CognitoError, CognitoUserTokens] =
        sdkCaller
          .authUser(email, password)
          .foldM(
            err =>
              log.throwable(s"Error while authing user email: ${email.value}", err) *>
                IO.fail(err match {
                  case _: NotAuthorizedException | _: UserNotFoundException => CognitoInvalidUsernameOrPasswordError
                  case _: UserNotConfirmedException                         => CognitoUserNotConfirmedError
                  case _: PasswordResetRequiredException                    => CognitoPasswordResetRequiredError
                  case e                                                    => GenericError(e.toString)
                }),
            tokens => IO.succeed(tokens)
          )

      override def sendForgotPasswordEmail(email: EmailAddress): IO[CognitoError, Unit] = sdkCaller
        .sendForgotPasswordEmail(email)
        .foldM(
          err =>
            log.throwable(s"Error while sending forgot email: ${email.value}", err) *>
              IO.fail(GenericError(err.toString)),
          tokens => IO.succeed(tokens)
        )

      override def getUserTz1Keys(accessToken: AccessToken): IO[CognitoError, UserTz1Keys] = sdkCaller
        .getUserTz1Keys(accessToken)
        .foldM(
          err =>
            err match {
              case _: NotAuthorizedException | _: UserNotFoundException =>
                IO.fail(CognitoInvalidUsernameOrPasswordError)
              case _: UserNotConfirmedException      => IO.fail(CognitoUserNotConfirmedError)
              case _: PasswordResetRequiredException => IO.fail(CognitoPasswordResetRequiredError)
              case _                                 => ZIO.succeed(UserTz1Keys("", ""))
            },
          tokens => IO.succeed(tokens)
        )
      override def getUserEmailByAccessToken(accessToken: AccessToken): IO[CognitoError, EmailAddress] = sdkCaller
        .getUserEmailByAccessToken(accessToken)
        .foldM(
          err =>
            IO.fail(err match {
              case _: NotAuthorizedException | _: UserNotFoundException => CognitoInvalidUsernameOrPasswordError
              case _: UserNotConfirmedException                         => CognitoUserNotConfirmedError
              case _: PasswordResetRequiredException                    => CognitoPasswordResetRequiredError
              case e                                                    => GenericError(e.toString)
            }),
          tokens => IO.succeed(tokens)
        )
      override def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): IO[CognitoError, Unit] =
        sdkCaller
          .updateUserTz1Keys(accessToken, keys)
          .foldM(
            err =>
              log.throwable(s"Error while updating keys: ${keys.publicKey} ${keys.publicKeyHash}", err) *>
                IO.fail(err match {
                  case _: NotAuthorizedException | _: UserNotFoundException => CognitoInvalidUsernameOrPasswordError
                  case _: UserNotConfirmedException                         => CognitoUserNotConfirmedError
                  case _: PasswordResetRequiredException                    => CognitoPasswordResetRequiredError
                  case e                                                    => GenericError(e.toString)
                }),
            _ => IO.unit
          )

      override def confirmForgotPassword(
        email: EmailAddress,
        newPassword: Password,
        code: String
      ): IO[CognitoError, Unit] =
        sdkCaller
          .confirmForgotPassword(email, newPassword, code)
          .foldM(
            err =>
              log.throwable(s"Error while sending forgot email: ${email.value}", err) *> IO.fail(err match {
                case _: NotAuthorizedException | _: UserNotFoundException => CognitoInvalidUsernameOrPasswordError
                case _: UserNotConfirmedException                         => CognitoUserNotConfirmedError
                case _: PasswordResetRequiredException                    => CognitoPasswordResetRequiredError
                case e                                                    => GenericError(e.toString)
              }),
            _ => IO.unit
          )

      override def refreshToken(userId: UserId, refreshToken: RefreshToken): IO[CognitoError, CognitoUserTokens] =
        sdkCaller
          .refreshToken(userId, refreshToken)
          .foldM(
            err =>
              log.throwable(s"Error while refreshing the token: ", err) *>
                IO.fail(err match {
                  case _: NotAuthorizedException | _: UserNotFoundException => CognitoInvalidUsernameOrPasswordError
                  case _: UserNotConfirmedException                         => CognitoUserNotConfirmedError
                  case _: PasswordResetRequiredException                    => CognitoPasswordResetRequiredError
                  case e                                                    => GenericError(e.toString)
                }),
            tokens => IO.succeed(tokens)
          )
    }).toLayer

  def createNewUser(
    email: EmailAddress,
    password: Password,
    firstName: FirstName,
    lastName: LastName,
    nationalId: NationalId
  ): ZIO[CognitoConnector, CognitoError, User] =
    ZIO.accessM(_.get.createNewUser(email, password, firstName, lastName, nationalId))

  def authUser(email: EmailAddress, password: Password): ZIO[CognitoConnector, CognitoError, CognitoUserTokens] =
    ZIO.accessM(_.get.authUser(email, password))

  def sendForgotPasswordEmail(email: EmailAddress): ZIO[CognitoConnector, CognitoError, Unit] =
    ZIO.accessM(_.get.sendForgotPasswordEmail(email))

  def confirmForgotPassword(
    email: EmailAddress,
    newPassword: Password,
    code: String
  ): ZIO[CognitoConnector, CognitoError, Unit] =
    ZIO.accessM(_.get.confirmForgotPassword(email, newPassword, code))

  def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): ZIO[CognitoConnector, CognitoError, Unit] =
    ZIO.accessM(_.get.updateUserTz1Keys(accessToken, keys))

  def getUserTz1Keys(accessToken: AccessToken): ZIO[CognitoConnector, CognitoError, UserTz1Keys] =
    ZIO.accessM(_.get.getUserTz1Keys(accessToken))

  def getUserEmailByAccessToken(accessToken: AccessToken): ZIO[CognitoConnector, CognitoError, EmailAddress] =
    ZIO.accessM(_.get.getUserEmailByAccessToken(accessToken))

  def refreshToken(userId: UserId, refreshToken: RefreshToken): ZIO[CognitoConnector, CognitoError, CognitoUserTokens] =
    ZIO.accessM(_.get.refreshToken(userId, refreshToken))
}
