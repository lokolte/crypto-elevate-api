package io.cryptoelevate.connectors.interfaces.cognito

import com.amazonaws.services.cognitoidp.model._
import io.cryptoelevate.connectors.interfaces.cognito.common.DummyCognitoSdkCaller._
import io.cryptoelevate.connectors.interfaces.cognito.common.{DummyCognitoSdkCaller, DummyData}
import io.cryptoelevate.connectors.errors._
import io.cryptoelevate.connectors.models.AccessToken
import io.cryptoelevate.model._
import io.cryptoelevate.{DummyCognitoData, Logger}
import zio.ZLayer
import zio.test.Assertion.{equalTo, fails, isUnit}
import zio.test._

object AwsCognitoConnectorSpec extends DefaultRunnableSpec with DummyData with DummyCognitoData {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Aws cognito connector spec")(
      createNewUserSuite,
      authUserSuite,
      refreshTokenSuite,
      sendForgotPasswordEmail,
      confirmForgotPasswordSuite,
      getUserTz1Keys,
      updateUserTz1Keys
    )

  private val createNewUserSuite = suite("Create new user")(
    testM("correctly returns new user data") {
      assertM(
        CognitoConnector
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(prepareEnv())
      )(equalTo(dummyUser))
    },
    testM("fails if password not satisfies constraint") {
      assertM(
        CognitoConnector
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(
            prepareEnv(
              createFailingCreateNewUser(new InvalidPasswordException("Password failed to satisfy constraint"))
            )
          )
          .run
      )(fails(equalTo(CognitoInvalidPasswordNotSatisfyConstraintError)))
    },
    testM("fails if national id exists") {
      assertM(
        CognitoConnector
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(
            prepareEnv(createFailingCreateNewUser(new InvalidParameterException("NationalId is already taken")))
          )
          .run
      )(fails(equalTo(CognitoInvalidNationalIdNotUniqueError)))
    },
    testM("fails if username exists") {
      assertM(
        CognitoConnector
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(prepareEnv(createFailingCreateNewUser(new UsernameExistsException("Username already exists"))))
          .run
      )(fails(equalTo(UsernameExistsError)))
    }
  )

  private val authUserSuite = suite("Auth user")(
    testM("returns tokens on successful auth") {
      assertM(CognitoConnector.authUser(dummyUser.email, Password("GoodP4ssw0rd!@#")).provideLayer(prepareEnv()))(
        equalTo(dummyAuthTokens)
      )
    },
    testM("fails if invalid credentials provided") {
      assertM(
        CognitoConnector
          .authUser(dummyUser.email, Password("bad_passw0rd!"))
          .provideLayer(prepareEnv(createFailingAuthUser(new NotAuthorizedException("Incorrect username or password"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if user not found") {
      assertM(
        CognitoConnector
          .authUser(dummyUser.email, Password("P4ssw0rd!23!@#"))
          .provideLayer(prepareEnv(createFailingAuthUser(new UserNotFoundException("Incorrect username or password"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if user not confirmed") {
      assertM(
        CognitoConnector
          .authUser(dummyUser.email, Password("GoodP4ssw0rd!@#"))
          .provideLayer(prepareEnv(createFailingAuthUser(new UserNotConfirmedException("User not confirmed"))))
          .run
      )(fails(equalTo(CognitoUserNotConfirmedError)))
    },
    testM("fails if user not found") {
      assertM(
        CognitoConnector
          .authUser(dummyUser.email, Password("GoodP4ssw0rd!@#"))
          .provideLayer(prepareEnv(createFailingAuthUser(new UserNotFoundException("User not found"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if password reset required") {
      assertM(
        CognitoConnector
          .authUser(dummyUser.email, Password("GoodP4ssw0rd!@#"))
          .provideLayer(
            prepareEnv(createFailingAuthUser(new PasswordResetRequiredException("Password reset required")))
          )
          .run
      )(fails(equalTo(CognitoPasswordResetRequiredError)))
    }
  )

  private val refreshTokenSuite = suite("Refresh Token")(
    testM("returns refreshed tokens") {
      assertM(CognitoConnector.refreshToken(dummyUser.sub, dummyAuthTokens.refreshToken).provideLayer(prepareEnv()))(
        equalTo(dummyAuthTokens)
      )
    },
    testM("fails if invalid credentials provided") {
      assertM(
        CognitoConnector
          .refreshToken(dummyUser.sub, dummyAuthTokens.refreshToken)
          .provideLayer(
            prepareEnv(createFailingRefreshToken(new NotAuthorizedException("Incorrect username or password")))
          )
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if user not found") {
      assertM(
        CognitoConnector
          .refreshToken(dummyUser.sub, dummyAuthTokens.refreshToken)
          .provideLayer(
            prepareEnv(createFailingRefreshToken(new UserNotFoundException("Incorrect username or password")))
          )
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if user not confirmed") {
      assertM(
        CognitoConnector
          .refreshToken(dummyUser.sub, dummyAuthTokens.refreshToken)
          .provideLayer(prepareEnv(createFailingRefreshToken(new UserNotConfirmedException("User not confirmed"))))
          .run
      )(fails(equalTo(CognitoUserNotConfirmedError)))
    },
    testM("fails if password reset required") {
      assertM(
        CognitoConnector
          .refreshToken(dummyUser.sub, dummyAuthTokens.refreshToken)
          .provideLayer(
            prepareEnv(createFailingRefreshToken(new PasswordResetRequiredException("Password reset required")))
          )
          .run
      )(fails(equalTo(CognitoPasswordResetRequiredError)))
    }
  )

  private val sendForgotPasswordEmail = suite("Send forgot password email")(
    testM("sends successfully") {
      assertM(CognitoConnector.sendForgotPasswordEmail(dummyUser.email).provideLayer(prepareEnv()))(isUnit)
    },
    testM("not fail on error") {
      assertM(
        CognitoConnector
          .sendForgotPasswordEmail(dummyUser.email)
          .provideLayer(
            prepareEnv(
              createFailingSendForgotPasswordEmail(new AWSCognitoIdentityProviderException("Something bad happen"))
            )
          )
          .run
      )(fails(equalTo(GenericError(new AWSCognitoIdentityProviderException("Something bad happen").toString))))
    }
  )

  private val confirmForgotPasswordSuite = suite("Confirm forgot password")(
    testM("confirms successfully") {
      assertM(
        CognitoConnector
          .confirmForgotPassword(dummyUser.email, Password("GoodP4ssw0rd!@#"), "12345")
          .provideLayer(prepareEnv())
      )(isUnit)
    },
    testM("fails if invalid password provided") {
      assertM(
        CognitoConnector
          .confirmForgotPassword(dummyUser.email, Password("badpassword"), "12345")
          .provideLayer(
            prepareEnv(
              createFailingConfirmForgotPassword(new InvalidPasswordException("Password failed to satisfy constraint"))
            )
          )
          .run
      )(fails(equalTo(GenericError(new InvalidPasswordException("Password failed to satisfy constraint").toString))))
    },
    testM("fails if invalid code provided") {
      assertM(
        CognitoConnector
          .confirmForgotPassword(dummyUser.email, Password("GoodP4ssw0rd!@#"), "111")
          .provideLayer(
            prepareEnv(createFailingConfirmForgotPassword(new CodeMismatchException("Incorrect validation code")))
          )
          .run
      )(fails(equalTo(GenericError(new CodeMismatchException("Incorrect validation code").toString))))
    },
    testM("fails if code expired") {
      assertM(
        CognitoConnector
          .confirmForgotPassword(dummyUser.email, Password("GoodP4ssw0rd!@#"), "12345")
          .provideLayer(
            prepareEnv(createFailingConfirmForgotPassword(new ExpiredCodeException("Forgot password code expired")))
          )
          .run
      )(fails(equalTo(GenericError(new ExpiredCodeException("Forgot password code expired").toString))))
    }
  )

  private val getUserTz1Keys = suite("Get user tz1 keys")(
    testM("Get keys successfully") {
      assertM(
        CognitoConnector
          .getUserTz1Keys(dummyAccessToken)
          .provideLayer(prepareEnv())
      )(equalTo(dummyTz1Keys))
    },
    testM("Fails if invalid token provided") {
      assertM(
        CognitoConnector
          .getUserTz1Keys(AccessToken("INVALID"))
          .provideLayer(prepareEnv(createFailingGetUserTz1Keys(new NotAuthorizedException("Not authorized"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("Fails if user not found") {
      assertM(
        CognitoConnector
          .getUserTz1Keys(AccessToken("123456"))
          .provideLayer(prepareEnv(createFailingGetUserTz1Keys(new UserNotFoundException("User not found"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("Fails if user not confirmed") {
      assertM(
        CognitoConnector
          .getUserTz1Keys(dummyAccessToken)
          .provideLayer(prepareEnv(createFailingGetUserTz1Keys(new UserNotConfirmedException("User not confirmed"))))
          .run
      )(fails(equalTo(CognitoUserNotConfirmedError)))
    },
    testM("Fails if password reset required") {
      assertM(
        CognitoConnector
          .getUserTz1Keys(dummyAccessToken)
          .provideLayer(
            prepareEnv(createFailingGetUserTz1Keys(new PasswordResetRequiredException("Password reset required")))
          )
          .run
      )(fails(equalTo(CognitoPasswordResetRequiredError)))
    }
  )

  private val updateUserTz1Keys = suite("Update user tz1 keys")(
    testM("Update keys successfully") {
      assertM(
        CognitoConnector
          .updateUserTz1Keys(dummyAccessToken, dummyTz1Keys)
          .provideLayer(prepareEnv())
      )(isUnit)
    },
    testM("fails if invalid token provided") {
      assertM(
        CognitoConnector
          .updateUserTz1Keys(dummyAccessToken, dummyTz1Keys)
          .provideLayer(prepareEnv(createFailingUpdateUserTz1Keys(new NotAuthorizedException("Not authorized"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if user not found") {
      assertM(
        CognitoConnector
          .updateUserTz1Keys(dummyAccessToken, dummyTz1Keys)
          .provideLayer(prepareEnv(createFailingUpdateUserTz1Keys(new UserNotFoundException("User not found"))))
          .run
      )(fails(equalTo(CognitoInvalidUsernameOrPasswordError)))
    },
    testM("fails if user not confirmed") {
      assertM(
        CognitoConnector
          .updateUserTz1Keys(dummyAccessToken, dummyTz1Keys)
          .provideLayer(prepareEnv(createFailingUpdateUserTz1Keys(new UserNotConfirmedException("User not confirmed"))))
          .run
      )(fails(equalTo(CognitoUserNotConfirmedError)))
    },
    testM("fails if password reset required") {
      assertM(
        CognitoConnector
          .updateUserTz1Keys(dummyAccessToken, dummyTz1Keys)
          .provideLayer(
            prepareEnv(createFailingUpdateUserTz1Keys(new PasswordResetRequiredException("Password reset required")))
          )
          .run
      )(fails(equalTo(CognitoPasswordResetRequiredError)))
    }
  )

  private def prepareEnv(sdkCaller: CognitoSdkCaller.Service = new DummyCognitoSdkCaller) =
    (Logger.liveEnv("aws-cognito") ++ ZLayer.succeed(sdkCaller)) >>> CognitoConnector.liveConnectorLayer
}
