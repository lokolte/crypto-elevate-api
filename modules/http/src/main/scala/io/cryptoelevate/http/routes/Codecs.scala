package io.cryptoelevate.http.routes

import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.semiauto.deriveEncoder
import io.cryptoelevate.dataservice.{ UserError, UserErrorCause }
import io.cryptoelevate.http.routes.Model.{ HttpError, Tz1, UserCreateRequest }
import io.cryptoelevate.model._

private[routes] trait Codecs {
  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit val decodeEmail: Decoder[EmailAddress] = (c: HCursor) =>
    c.value.asString match {
      case Some(str) if StringValidator.isValidEmail(str) => Right(EmailAddress(str.trim))
      case _ =>
        Left(DecodingFailure("Not valid email format should be: name@domain.com", Nil))
    }

  implicit val decodePassword: Decoder[Password] = (c: HCursor) =>
    c.value.asString match {
      case Some(str) if StringValidator.isValidPassword(str) => Right(Password(str.trim))
      case _ =>
        Left(
          DecodingFailure(
            "Not valid password should be: min 8 chars, contain at least: 1 letter, upper and lower case, one special character",
            Nil
          )
        )
    }

  implicit val decodeUserCreateRequest: Decoder[UserCreateRequest] = (c: HCursor) =>
    for {
      firstName        <- c.downField("first_name").as[String]
      lastName         <- c.downField("last_name").as[String]
      email            <- c.downField("email").as[EmailAddress]
      nationalIdNumber <- c.downField("national_id_number").as[String]
      password         <- c.downField("password").as[Password]
    } yield UserCreateRequest(FirstName(firstName), LastName(lastName), email, NationalId(nationalIdNumber), password)

  implicit val encodeHttpError: Encoder[HttpError] = (error: HttpError) =>
    Json.obj(("msg", Json.fromString(error.msg)), ("cause", Json.fromString(error.cause)))

  implicit val encoderUserErrorCause: Encoder[UserErrorCause] = deriveEncoder
  implicit val encoderUserError: Encoder[UserError] = deriveEncoder

}
