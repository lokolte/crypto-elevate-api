package io.cryptoelevate.connectors.interfaces.dynamo

import io.cryptoelevate.connectors.interfaces.dynamo.common.{DummyData, DummyDynamoSdkCaller}
import io.cryptoelevate.connectors.errors._
import io.cryptoelevate.connectors.models.{UserTz1Keys}
import io.cryptoelevate.model._
import io.cryptoelevate.Logger
import io.cryptoelevate.connectors.interfaces.dynamo.common.DummyDynamoSdkCaller._
import zio.ZLayer
import zio.test.Assertion.{equalTo, fails, isUnit}
import zio.test._

object AwsDynamoConnectorSpec extends DefaultRunnableSpec with DummyData {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Aws dynamo connector spec")(
      moveTz1KeySuite,
      burnTz1KeysSuite,
      createNewUserSuite,
      updateUserKeysSuite,
      getUserTz1KeysSuite,
    )

  private val moveTz1KeySuite = suite("Move Tz1 Key")(
    testM("returns User History updated") {
      assertM(
        DynamoConnector
          .moveUserTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv()
          )
      )(equalTo(dummyUserHistory))
    },
    testM("fails if email doesn't exist in dynamo") {
      assertM(
        DynamoConnector
          .moveUserTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv(createFailingMoveTz1Key(new NoSuchElementException))
          )
          .run
      )(fails(equalTo(DynamoEmailNotFoundError)))
    },
    testM("fails if tz1 is invalid") {
      assertM(
        DynamoConnector
          .moveUserTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv(createFailingMoveTz1Key(new IllegalArgumentException))
          )
          .run
      )(fails(equalTo(DynamoInvalidTZ1KeyError)))
    },
    testM("unhandled exception from dynamo") {
      assertM(
        DynamoConnector
          .moveUserTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv(
              createFailingMoveTz1Key(new Exception("Error"))
            )
          )
          .run
      )(fails(equalTo(DynamoGenericError(new Exception("Error").toString))))
    }
  )

  private val burnTz1KeysSuite = suite("Burn Tz1 Key")(
    testM("returns User History updated") {
      assertM(
        DynamoConnector
          .burnTz1Keys(
            email = EmailAddress("john@doe.io"),
            tz1Key = UserTz1Keys("abc_123", "987_123")
          ).provideLayer(prepareEnv()))(
        equalTo(dummyUserHistory)
      )
    },
    testM("fails if email doesn't exist in dynamo") {
      assertM(
        DynamoConnector
          .burnTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv(createFailingBurnTz1Key(new NoSuchElementException))
          )
          .run
      )(fails(equalTo(DynamoEmailNotFoundError)))
    },
    testM("fails if tz1 is not as inactive or was already burned") {
      assertM(
        DynamoConnector
          .burnTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv(createFailingBurnTz1Key(new IllegalArgumentException))
          )
          .run
      )(fails(equalTo(DynamoTZ1KeyNotExistError)))
    },
    testM("unhandled exception from dynamo") {
      assertM(
        DynamoConnector
          .burnTz1Keys(
            email = dummyEmail,
            tz1Key = dummyActualTz1Key
          )
          .provideLayer(
            prepareEnv(createFailingBurnTz1Key(new Exception("Error")))
          )
          .run
      )(fails(equalTo(DynamoGenericError(new Exception("Error").toString))))
    }
  )

  private val createNewUserSuite = suite("Create new user")(
    testM("correctly returns new user data") {
      assertM(
        DynamoConnector
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            actualTZ1Key = Some(UserTz1Keys("abc_123", "987_123")),
            burnedTZ1Keys = List(UserTz1Keys("abc_1234", "987_1234"))
          )
          .provideLayer(prepareEnv())
      )(isUnit)
    },
    testM("fails if password not satisfies constraint") {
      assertM(
        DynamoConnector
          .createNewUser(
            email = EmailAddress("john@doe.io"),
            actualTZ1Key = Some(UserTz1Keys("abc_123", "987_123")),
            burnedTZ1Keys = List(UserTz1Keys("abc_1234", "987_1234"))
          )
          .provideLayer(prepareEnv(createFailingCreateNewUser(new Exception("Error"))))
          .run
      )(fails(equalTo(DynamoGenericError(new Exception("Error").toString))))
    }
  )

  private val updateUserKeysSuite = suite("Update user Tz1 Keys")(
    testM("returns User History updated") {
      assertM(
        DynamoConnector
          .updateUserTz1Keys(
            email = dummyEmail,
            keys = List(dummyActualTz1Key)
          )
          .provideLayer(
            prepareEnv()
          )
      )(isUnit)
    },
    testM("fails if something fails with dynamo") {
      assertM(
        DynamoConnector
          .updateUserTz1Keys(
            email = EmailAddress("john@doe.io"),
            keys = List(dummyActualTz1Key)
          )
          .provideLayer(prepareEnv(createFailingUpdateUserTz1Keys(new Exception("Error"))))
          .run
      )(fails(equalTo(DynamoGenericError(new Exception("Error").toString))))
    }
  )

  private val getUserTz1KeysSuite = suite("Get UserHistory with Tz1 Keys")(
    testM("returns User History updated") {
      assertM(
        DynamoConnector
          .getUserTz1Keys(
            email = dummyEmail
          )
          .provideLayer(
            prepareEnv()
          )
      )(equalTo(dummyUserHistory))
    },
    testM("fails if something fails with dynamo") {
      assertM(
        DynamoConnector
          .getUserTz1Keys(
            email = EmailAddress("john@doe.io")
          )
          .provideLayer(prepareEnv(createFailingGetUserTz1Keys(new Exception("Error"))))
          .run
      )(fails(equalTo(DynamoGenericError(new Exception("Error").toString))))
    }
  )

  private def prepareEnv(sdkCaller: DynamoSdkCaller.Service = new DummyDynamoSdkCaller) =
    (Logger.liveEnv("aws-dynamo") ++ ZLayer.succeed(sdkCaller)) >>> DynamoConnector.liveConnectorLayer
}
