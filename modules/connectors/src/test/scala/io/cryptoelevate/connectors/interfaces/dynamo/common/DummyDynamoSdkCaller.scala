package io.cryptoelevate.connectors.interfaces.dynamo.common

import io.cryptoelevate.connectors.errors.DynamoError
import io.cryptoelevate.connectors.interfaces.dynamo.DynamoSdkCaller
import io.cryptoelevate.connectors.models.{UserHistory, UserTz1Keys}
import io.cryptoelevate.model._
import zio.{IO, Task, ZIO}

object DummyDynamoSdkCaller {
  def createFailingMoveTz1Key(error: Throwable): DynamoSdkCaller.Service = {
    class Failing extends DummyDynamoSdkCaller {
      override def moveTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory] =
        IO.fail(error)
    }
    new Failing
  }
  def createFailingBurnTz1Key(error: Throwable): DynamoSdkCaller.Service = {
    class Failing extends DummyDynamoSdkCaller {
      override def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory] =
        IO.fail(error)
    }
    new Failing
  }
  def createFailingCreateNewUser(error: Throwable): DynamoSdkCaller.Service = {
    class Failing extends DummyDynamoSdkCaller {
      override def createNewUser(
        email: EmailAddress,
        actualTZ1Key: Option[UserTz1Keys],
        inactiveTZ1Keys: List[UserTz1Keys] = List(),
      burnedTZ1Keys: List[UserTz1Keys] = List()
      ): ZIO[Any, Throwable, Unit] = Task.fail(error)
    }
    new Failing
  }
  def createFailingUpdateUserTz1Keys(error: Throwable): DynamoSdkCaller.Service = {
    class Failing extends DummyDynamoSdkCaller {
      override def updateUserKeys(
                                   email: EmailAddress,
                                   actual: Option[UserTz1Keys] = None,
                                   inactiveTZ1Keys: List[UserTz1Keys] = List(),
                                   burnedTZ1Keys: List[UserTz1Keys] = List()
                                 ): Task[UserHistory] = Task.fail(error)
    }
    new Failing
  }
  def createFailingGetUserTz1Keys(error: Throwable): DynamoSdkCaller.Service = {
    class Failing extends DummyDynamoSdkCaller {
      override def getUserTz1Keys(email: EmailAddress): Task[Option[UserHistory]] =
        IO.fail(error)
    }
    new Failing
  }
}

class DummyDynamoSdkCaller extends DynamoSdkCaller.Service with DummyData {
  def moveTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory] = Task.succeed(dummyUserHistory)
  def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory] = Task.succeed(dummyUserHistory)

  def createNewUser(
                     email: EmailAddress,
                     actualTZ1Key: Option[UserTz1Keys],
                     inactiveTZ1Keys: List[UserTz1Keys] = List(),
                     burnedTZ1Keys: List[UserTz1Keys] = List()
                   ): ZIO[Any, Throwable, Unit] = Task.succeed()

  def updateUserKeys(
                      email: EmailAddress,
                      actual: Option[UserTz1Keys] = None,
                      inactiveTZ1Keys: List[UserTz1Keys] = List(),
                      burnedTZ1Keys: List[UserTz1Keys] = List()
                    ): Task[UserHistory] = Task.succeed(dummyUserHistory)

  def getUserTz1Keys(email: EmailAddress): Task[Option[UserHistory]] = Task.succeed(Some(dummyUserHistory))
}
