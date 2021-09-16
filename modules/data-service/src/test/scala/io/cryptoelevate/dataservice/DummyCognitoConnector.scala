package io.cryptoelevate.dataservice

import io.cryptoelevate.DummyCognitoData
import io.cryptoelevate.connectors.interfaces.cognito.common.DummyData
import io.cryptoelevate.connectors.models._

import io.cryptoelevate.connectors.errors.CognitoError
import io.cryptoelevate.connectors.interfaces.cognito.CognitoConnector
import io.cryptoelevate.model._
import zio.IO

object DummyCognitoConnector {

  def createFailingCreateNewUser(error: CognitoError): CognitoConnector.Service = {
    class Failing extends DummyCognitoConnector {
      override def createNewUser(
        email: EmailAddress,
        password: Password,
        firstName: FirstName,
        lastName: LastName,
        nationalId: NationalId
      ): IO[CognitoError, User] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingAuthUser(error: CognitoError): CognitoConnector.Service = {
    class Failing extends DummyCognitoConnector {
      override def authUser(email: EmailAddress, password: Password): IO[CognitoError, CognitoUserTokens] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingSendForgotPasswordEmail(error: CognitoError): CognitoConnector.Service = {
    class Failing extends DummyCognitoConnector {
      override def sendForgotPasswordEmail(email: EmailAddress): IO[CognitoError, Unit] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingConfirmForgotPassword(error: CognitoError): CognitoConnector.Service = {
    class Failing extends DummyCognitoConnector {
      override def confirmForgotPassword(
        email: EmailAddress,
        newPassword: Password,
        code: String
      ): IO[CognitoError, Unit] =
        IO.fail(error)
    }

    new Failing
  }

  def apply(): CognitoConnector.Service = new DummyCognitoConnector

}

class DummyCognitoConnector extends CognitoConnector.Service with DummyCognitoData with DummyData {

  override def createNewUser(
    email: EmailAddress,
    password: Password,
    firstName: FirstName,
    lastName: LastName,
    nationalId: NationalId
  ): IO[CognitoError, User] = IO.succeed(dummyUser)

  override def authUser(email: EmailAddress, password: Password): IO[CognitoError, CognitoUserTokens] =
    IO.succeed(dummyAuthTokens)

  override def sendForgotPasswordEmail(email: EmailAddress): IO[CognitoError, Unit] = IO.unit

  override def confirmForgotPassword(email: EmailAddress, newPassword: Password, code: String): IO[CognitoError, Unit] =
    IO.unit

  override def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): IO[CognitoError, Unit] = IO.unit

  override def getUserTz1Keys(accessToken: AccessToken): IO[CognitoError, UserTz1Keys] = IO.succeed(dummyTz1Keys)

  override def refreshToken(userId: UserId, refreshToken: RefreshToken): IO[CognitoError, CognitoUserTokens] =
    IO.succeed(dummyAuthTokens)

  override def getUserEmailByAccessToken(accessToken: AccessToken): IO[CognitoError, EmailAddress] =
    IO.succeed(dummyEmail)
}
