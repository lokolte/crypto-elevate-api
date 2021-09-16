package io.cryptoelevate.connectors

import java.util.UUID

import io.cryptoelevate.model.EmailAddress
import javax.mail.internet.InternetAddress

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

package object models {
  final case class AccessToken(value: String) extends AnyVal
  final case class RefreshToken(value: String) extends AnyVal
  final case class TokenExpiration(value: Int) extends AnyVal {
    def asFiniteDuration: FiniteDuration = value.seconds
  }
  final case class CognitoUserTokens private (
    accessToken: AccessToken,
    refreshToken: RefreshToken,
    expiresIn: TokenExpiration
  )

  object CognitoUserTokens {
    def fromRaw(accessToken: String, refreshToken: String, expiresIn: Int): CognitoUserTokens =
      CognitoUserTokens(
        accessToken = AccessToken(accessToken),
        refreshToken = RefreshToken(refreshToken),
        expiresIn = TokenExpiration(expiresIn)
      )
  }
  final case class UserHistory private (
    email: EmailAddress,
    actualTZ1Keys: Option[UserTz1Keys],
    inactiveTZ1Keys: List[UserTz1Keys],
    burnedTZ1Keys: List[UserTz1Keys]
  )
  case class Address(address: String, personal: Option[String], charset: String, encoded: String)

  object Address {
    def apply(address: String, personal: Option[String] = None, charset: String = "UTF-8"): Address = {
      val encoded = personal
        .map(new InternetAddress(address, _, charset))
        .getOrElse(new InternetAddress(address))
        .toString
      new Address(address, personal, charset, encoded)
    }
  }

  case class Content(data: String, charset: String = "UTF-8")

  case class SendingEmail(
    subject: Content,
    source: Address,
    bodyText: Option[Content] = None,
    bodyHtml: Option[Content] = None,
    to: Seq[Address] = Seq.empty,
    cc: Seq[Address] = Seq.empty,
    bcc: Seq[Address] = Seq.empty,
    replyTo: Seq[Address] = Seq.empty,
    returnPath: Option[String] = None,
    configurationSet: Option[String] = None,
    messageTags: Map[String, String] = Map.empty
  )

  final case class UserTz1Keys(publicKey: String, publicKeyHash: String)

  final case class RedisUser(uuid: UUID, email: EmailAddress, tz1: UserTz1Keys) //TODO: make those types better

  // TODO: make some test to prove that this structure is enough
  final case class LambdaRequest(body: Body)
  final case class Body(oldAddress: String, newAddress: String)

  // TODO: Change by the real response structure
  final case class LambdaResponse()
}
