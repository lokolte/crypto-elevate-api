package io.cryptoelevate

import io.cryptoelevate.model._
import java.util.UUID

trait DummyCognitoData {
  final val dummyUser: User =
    User(
      sub = UserId(UUID.fromString("13fac0b5-f0c0-4de4-a2ca-97209949591f")),
      email = EmailAddress("john@doe.io"),
      firstName = FirstName("John"),
      lastName = LastName("Doe"),
      nationalId = NationalId("11-22-33-44-55")
    )

}
