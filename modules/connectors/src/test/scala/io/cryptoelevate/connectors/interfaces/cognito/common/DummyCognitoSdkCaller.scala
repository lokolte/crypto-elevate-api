package io.cryptoelevate.connectors.interfaces.cognito.common

import io.cryptoelevate.DummyCognitoData
import io.cryptoelevate.connectors.interfaces.cognito.CognitoSdkCaller
import io.cryptoelevate.connectors.models.{ AccessToken, CognitoUserTokens, RefreshToken, UserTz1Keys }
import io.cryptoelevate.model._
import zio.{ IO, Task }

object DummyCognitoSdkCaller {
  def createFailingCreateNewUser(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def createNewUser(
        email: EmailAddress,
        password: Password,
        firstName: FirstName,
        lastName: LastName,
        nationalId: NationalId
      ): Task[User] =
        Task.fail(error)

    }

    new Failing
  }
  def createFailingAuthUser(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def authUser(email: EmailAddress, password: Password): Task[CognitoUserTokens] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingGetUserEmail(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def getUserEmailByAccessToken(accessToken: AccessToken): Task[EmailAddress] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingRefreshToken(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def refreshToken(userId: UserId, refreshToken: RefreshToken): Task[CognitoUserTokens] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingSendForgotPasswordEmail(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def sendForgotPasswordEmail(email: EmailAddress): Task[Unit] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingConfirmForgotPassword(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def confirmForgotPassword(email: EmailAddress, newPassword: Password, code: String): Task[Unit] =
        IO.fail(error)
    }

    new Failing
  }

  def createFailingGetUserTz1Keys(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def getUserTz1Keys(accessToken: AccessToken): Task[UserTz1Keys] = IO.fail(error)
    }

    new Failing
  }

  def createFailingUpdateUserTz1Keys(error: Throwable): CognitoSdkCaller.Service = {
    class Failing extends DummyCognitoSdkCaller {
      override def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): Task[Unit] = IO.fail(error)
    }

    new Failing
  }
}

class DummyCognitoSdkCaller extends CognitoSdkCaller.Service with DummyCognitoData with DummyData {
  override def createNewUser(
    email: EmailAddress,
    password: Password,
    firstName: FirstName,
    lastName: LastName,
    nationalId: NationalId
  ): Task[User] = Task.succeed(dummyUser)

  override def authUser(email: EmailAddress, password: Password): Task[CognitoUserTokens] =
    Task.succeed(dummyAuthTokens)

  override def sendForgotPasswordEmail(email: EmailAddress): Task[Unit] =
    Task.unit

  override def confirmForgotPassword(email: EmailAddress, newPassword: Password, code: String): Task[Unit] =
    Task.unit

  override def getUserTz1Keys(accessToken: AccessToken): Task[UserTz1Keys] = Task.succeed(dummyTz1Keys)

  override def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): Task[Unit] =
    Task.unit

  override def refreshToken(userId: UserId, refreshToken: RefreshToken): Task[CognitoUserTokens] =
    Task.succeed(dummyAuthTokens)

  override def getUserEmailByAccessToken(accessToken: AccessToken): Task[EmailAddress] = Task.succeed(dummyEmail)
}
