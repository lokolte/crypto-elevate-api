package io.cryptoelevate.connectors.interfaces.dynamo.common

import io.cryptoelevate.connectors.models._
import io.cryptoelevate.model.EmailAddress

trait DummyData {
  final val dummyEmail: EmailAddress = EmailAddress("john@doe.io")

  final val dummyActualTz1Key: UserTz1Keys = UserTz1Keys("abc_123", "987_123")

  final val dummyInactiveTz1Key: UserTz1Keys = UserTz1Keys("abc_1234", "987_1234")

  final val dummyBurnedTz1Key: UserTz1Keys = UserTz1Keys("abc_12345", "987_12345")

  final val dummyUserHistory: UserHistory = UserHistory(dummyEmail, Some(dummyActualTz1Key), List(dummyInactiveTz1Key), List(dummyBurnedTz1Key))
}
