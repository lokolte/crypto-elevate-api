package io.cryptoelevate.dataservice

import io.cryptoelevate.DummyCognitoData
import io.cryptoelevate.connectors.interfaces.cognito.common.DummyData
import io.cryptoelevate.connectors.errors.DynamoError
import io.cryptoelevate.connectors.interfaces.dynamo.DynamoConnector
import io.cryptoelevate.connectors.models._
import io.cryptoelevate.model._
import zio.IO

object DummyDynamoConnector {

  def apply(): DynamoConnector.Service = new DummyDynamoConnector

}

class DummyDynamoConnector extends DynamoConnector.Service with DummyCognitoData with DummyData {

  final val dummyHistoryUser: UserHistory =
    UserHistory( //TODO: Move this to DummyData package in tests
      email = EmailAddress("john@doe.io"),
      actualTZ1Keys = Some(UserTz1Keys("aa", "bb")),
      inactiveTZ1Keys = List(UserTz1Keys("ac", "bd")),
      burnedTZ1Keys = List(UserTz1Keys("bc", "de"))
    )

  override def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): IO[DynamoError, UserHistory] =
    IO.succeed(dummyHistoryUser)

  override def createNewUser(
    email: EmailAddress,
    actualTZ1Key: Option[UserTz1Keys],
    burnedTZ1Keys: List[UserTz1Keys]
  ): IO[DynamoError, Unit] = IO.succeed()

  override def updateUserTz1Keys(email: EmailAddress, keys: List[UserTz1Keys]): IO[DynamoError, Unit] = IO.unit

  def moveUserTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): IO[DynamoError, UserHistory] =
    IO.succeed(dummyHistoryUser)

  override def getUser(email: EmailAddress): IO[DynamoError, UserHistory] = IO.succeed(dummyHistoryUser)
}
