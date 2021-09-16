package io.cryptoelevate

import java.time.Instant
import java.util.UUID

package object model {

  final case class EmailAddress(value: String) extends AnyVal

  final case class Password(value: String) extends AnyVal
  final case class FirstName(value: String) extends AnyVal
  final case class LastName(value: String) extends AnyVal
  final case class NationalId(value: String) extends AnyVal

  final case class UserId private (value: UUID) extends AnyVal
  final case class Username(value: String) extends AnyVal
  final case class User(
    sub: UserId,
    email: EmailAddress,
    firstName: FirstName,
    lastName: LastName,
    nationalId: NationalId
  )

  final case class AuthContext(userId: UserId, email: EmailAddress, expiresAt: Instant)

}
