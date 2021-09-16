package io.cryptoelevate.http.routes

import io.circe.generic.extras._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor }
import io.cryptoelevate.dataservice.{ UserError, UserErrorCause }
import io.cryptoelevate.model._

import java.util.UUID

object Model extends Codecs {

  @ConfiguredJsonCodec(encodeOnly = true)
  final case class UserResponse(sub: UUID, email: String, firstName: String, lastName: String, nationalId: String)

  final case class UserCreateRequest(
    firstName: FirstName,
    lastName: LastName,
    email: EmailAddress,
    nationalIdNumber: NationalId,
    password: Password
  )

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class UserAuthRequest(email: EmailAddress, password: Password)

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class RefreshTokenRequest(accessToken: String, refreshToken: String)

  @ConfiguredJsonCodec
  final case class RefreshTokenResponse(accessToken: String, refreshToken: String, expiresIn: Int)
  @ConfiguredJsonCodec
  final case class TokensResponse(tz1: Tz1, refreshTokenResponse: RefreshTokenResponse)

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class UserForgotPasswordRequest(email: EmailAddress)

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class UserForgotPasswordChangeRequest(email: EmailAddress, password: Password, code: String)

  @ConfiguredJsonCodec
  final case class Tz1(publicKey: String, publicKeyHash: String)

  sealed trait HttpError {
    val msg: String
    val cause: String
  }

  final case class HttpDecodingError(msg: String) extends HttpError {
    override val cause: String = "Decoding error"
  }

  final case class HttpInternalServerError(msg: String, cause: String) extends HttpError
  case object HttpUnauthorizedError extends HttpError {
    override val msg: String = "Invalid credentials"
    override val cause: String = ""
  }
  final case class HttpBadRequestError(msg: String, cause: String) extends HttpError
  final case class HttpNotFoundError(msg: String, cause: String) extends HttpError
  final case class HttpConflictError(msg: String, cause: String) extends HttpError
}
