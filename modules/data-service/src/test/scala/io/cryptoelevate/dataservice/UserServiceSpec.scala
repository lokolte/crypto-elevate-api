package io.cryptoelevate.dataservice

import io.cryptoelevate.connectors.interfaces.redis.RedisConnector
import io.cryptoelevate.connectors.interfaces.ses.SesConnector
import io.cryptoelevate.connectors.interfaces.cognito.CognitoConnector
import io.cryptoelevate.connectors.interfaces.cognito.common.DummyData
import io.cryptoelevate.connectors.errors._
import io.cryptoelevate.connectors.interfaces.dynamo.DynamoConnector
import io.cryptoelevate.connectors.interfaces.lambda.LambdaConnector
import io.cryptoelevate.model._
import io.cryptoelevate.DummyCognitoData
import zio.ZLayer
import zio.test.Assertion.{ equalTo, fails, isUnit }
import zio.test._
import io.cryptoelevate.dataservice.DummyCognitoConnector._

object UserServiceSpec extends DefaultRunnableSpec with DummyCognitoData with DummyData {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("User service spec")(createNewUserSuite, authUserSuite, sendForgotPasswordEmail, confirmForgotPasswordSuite)

  private val createNewUserSuite = suite("Create new user")(
    testM("correctly returns new user data") {
      assertM(
        UserService
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
    testM("correctly returns new user data when email contains whitespaces") {
      assertM(
        UserService
          .createNewUser(
            email = EmailAddress(" john@doe.io "),
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
        UserService
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(prepareEnv(createFailingCreateNewUser(CognitoInvalidPasswordNotSatisfyConstraintError)))
          .run
      )(fails(equalTo(UserError("Password failed to satisfy constraint", UserGenericError))))
    },
    testM("fails if national id exists") {
      assertM(
        UserService
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(prepareEnv(createFailingCreateNewUser(CognitoInvalidNationalIdNotUniqueError)))
          .run
      )(fails(equalTo(UserError("NationalId is already taken", UserGenericError))))
    },
    testM("fails if username exists") {
      assertM(
        UserService
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            password = Password("!QAZ#EDCbgt5mju7"),
            firstName = FirstName("John"),
            lastName = LastName("Doe"),
            nationalId = NationalId("11-22-33-44-55")
          )
          .provideLayer(prepareEnv(createFailingCreateNewUser(UsernameExistsError)))
          .run
      )(fails(equalTo(UserError("Username already exists", UserGenericError))))
    }
  )

  private val authUserSuite = suite("Auth user")(
    testM("returns tokens on successful auth") {
      assertM(UserService.authUser(dummyUser.email, Password("GoodP4ssw0rd!@#")).provideLayer(prepareEnv()))(
        equalTo(dummyAuthTokens)
      )
    },
    testM("fails if invalid credentials provided") {
      assertM(
        UserService
          .authUser(dummyUser.email, Password("bad_passw0rd!"))
          .provideLayer(prepareEnv(createFailingAuthUser(CognitoInvalidUsernameOrPasswordError)))
          .run
      )(fails(equalTo(UserError("Incorrect username or password", UserNotAuthorized))))
    }
  )

  private val sendForgotPasswordEmail = suite("Send forgot password email")(
    testM("sends successfully") {
      assertM(UserService.sendForgotPasswordEmail(dummyUser.email).provideLayer(prepareEnv()))(isUnit)
    },
    testM("not fail on error") {
      assertM(
        UserService
          .sendForgotPasswordEmail(dummyUser.email)
          .provideLayer(prepareEnv(createFailingSendForgotPasswordEmail(GenericError("error"))))
      )(isUnit)
    }
  )

  private val confirmForgotPasswordSuite = suite("Confirm forgot password")(
    testM("confirms successfully") {
      assertM(
        UserService
          .confirmForgotPassword(dummyUser.email, Password("GoodP4ssw0rd!@#"), "12345")
          .provideLayer(prepareEnv())
      )(isUnit)
    },
    testM("fails if invalid password provided") {
      assertM(
        UserService
          .confirmForgotPassword(dummyUser.email, Password("badpassword"), "12345")
          .provideLayer(prepareEnv(createFailingConfirmForgotPassword(CognitoInvalidPasswordNotSatisfyConstraintError)))
          .run
      )(fails(equalTo(UserError("Password failed to satisfy constraint", UserGenericError))))
    },
    testM("fails if invalid code provided") {
      assertM(
        UserService
          .confirmForgotPassword(dummyUser.email, Password("GoodP4ssw0rd!@#"), "111")
          .provideLayer(prepareEnv(createFailingConfirmForgotPassword(CognitoInvalidPasswordChangeValidationCodeError)))
          .run
      )(fails(equalTo(UserError("Incorrect validation code", UserGenericError))))
    },
    testM("fails if code expired") {
      assertM(
        UserService
          .confirmForgotPassword(dummyUser.email, Password("GoodP4ssw0rd!@#"), "12345")
          .provideLayer(prepareEnv(createFailingConfirmForgotPassword(CognitoForgotPasswordCodeExpiredError)))
          .run
      )(fails(equalTo(UserError("Forgot password code expired", UserGenericError))))
    }
  )

  private def prepareEnv(
    cognitoLayer: CognitoConnector.Service = DummyCognitoConnector(),
    sesLayer: SesConnector.Service = DummyAwsSesConnector(),
    redisLayer: RedisConnector.Service = DummyRedisConnector(),
    dynamoDbLayer: DynamoConnector.Service = DummyDynamoConnector(),
    lambdaLayer: LambdaConnector.Service = DummyLambdaConnector()
  ) =
    ZLayer.succeed(cognitoLayer) ++
      ZLayer.succeed(redisLayer) ++
      ZLayer.succeed(dynamoDbLayer) ++
      ZLayer.succeed(sesLayer) ++
      ZLayer.succeed(lambdaLayer) >>> UserService.live
}
