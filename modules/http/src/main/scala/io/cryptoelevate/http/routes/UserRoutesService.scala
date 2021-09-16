package io.cryptoelevate.http.routes


import io.circe.{ parser, Decoder, Encoder }

import io.cryptoelevate.connectors.models.{ AccessToken, UserTz1Keys }
import io.cryptoelevate.dataservice.{
  DynamoDbEmailNotFoundError,
  DynamoDbInvalidTz1Error,
  UserNotAuthorized,
  UserServerError,
  UserService
}
import io.cryptoelevate.http.routes.Model._
import io.cryptoelevate.model.UserId
import org.http4s._
import org.http4s.circe.{ jsonEncoderOf, toMessageSynax }
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import zio.{ IO, _ }
import zio.interop.catz._

import java.util.{ Base64, UUID }
import io.circe.generic.auto.exportEncoder

import scala.util.Try

object UserRoutesService extends UnauthorizedResponseSupport {

  def routes[R <: UserService](): HttpRoutes[RIO[R, *]] = {
    type UsersTask[A] = RIO[R, A]

    val dsl: Http4sDsl[UsersTask] = Http4sDsl[UsersTask]
    import dsl._

    implicit def circeJsonEncoder[A: Encoder]: EntityEncoder[UsersTask, A] = jsonEncoderOf[UsersTask, A]

    //TODO decodeRequest and getAccessToken can be moved to a trait
    def decodeRequest[A](req: Request[UsersTask])(implicit d: Decoder[A]): ZIO[R, HttpDecodingError, A] = for {
      decodedJson <- req.asJson.mapError { decodingError =>
        HttpDecodingError(msg = decodingError.getMessage)
      }
      decodedRequest <- Task
        .fromEither(decodedJson.as[A])
        .mapError(decodingError => HttpDecodingError(msg = decodingError.getMessage))
    } yield decodedRequest

    def getAccessToken(req: Request[UsersTask]): IO[HttpError, AccessToken] = ZIO.fromEither(
      req.headers
        .get(Authorization)
        .flatMap { t =>
          t.credentials match {
            case Credentials.Token(scheme, token) if scheme == AuthScheme.Bearer =>
              Some(AccessToken(token))
            case _ => None
          }
        }
        .toRight(HttpUnauthorizedError)
    )

    def extractUserId(accessToken: String): IO[HttpError, UserId] = for {
      jwtTokenPayload <- ZIO.fromEither(accessToken.split('.').drop(1).headOption.toRight(HttpUnauthorizedError))
      decodedPayload <- ZIO
        .fromTry(Try(new String(Base64.getDecoder.decode(jwtTokenPayload))))
        .orElseFail(HttpUnauthorizedError)
      sub <- ZIO
        .fromOption(parser.parse(decodedPayload).toOption.flatMap(_.\\("sub").headOption.flatMap(_.as[UUID].toOption)))
        .orElseFail(HttpUnauthorizedError)
    } yield UserId(sub)

    HttpRoutes.of[UsersTask] {
      case req @ POST -> Root / "refresh_token" =>
        (for {
          decodedRequest <- decodeRequest[RefreshTokenRequest](req)
          userId         <- extractUserId(decodedRequest.accessToken)
          resp <- UserService
            .refreshToken(userId, decodedRequest.refreshToken)
            .bimap(
              error =>
                error.cause match {
                  case UserServerError   => HttpInternalServerError(error.msg, error.cause.toString)
                  case UserNotAuthorized => HttpUnauthorizedError
                  case _                 => HttpBadRequestError(error.msg, error.cause.toString)
                },
              r => RefreshTokenResponse(r.accessToken.value, decodedRequest.refreshToken, r.expiresIn.value)
            )
        } yield resp).foldM(
          {
            case error: HttpInternalServerError               => InternalServerError(error.asInstanceOf[HttpError])
            case HttpUnauthorizedError | _: HttpDecodingError => Unauthorized(withChallenge("Invalid credentials"))
            case error: HttpError                             => BadRequest(error.asInstanceOf[HttpError])
          },
          Ok(_)
        )

      case req @ POST -> Root / "signin" =>
        (for {
          decodedRequest <- decodeRequest[UserAuthRequest](req)
          refresh <- UserService
            .authUser(decodedRequest.email, decodedRequest.password)
            .bimap(
              error =>
                error.cause match {
                  case UserServerError   => HttpInternalServerError(error.msg, error.cause.toString)
                  case UserNotAuthorized => HttpUnauthorizedError
                  case _                 => HttpBadRequestError(error.msg, error.cause.toString)
                },
              r => RefreshTokenResponse(r.accessToken.value, r.refreshToken.value, r.expiresIn.value)
            )
          response <-
            UserService
              .getUserTz1Keys(AccessToken(refresh.accessToken))
              .bimap(
                error =>
                  error.cause match {
                    case UserServerError   => HttpInternalServerError(error.msg, error.cause.toString)
                    case UserNotAuthorized => HttpUnauthorizedError
                    case _                 => HttpBadRequestError(error.msg, error.cause.toString)
                  },
                r => TokensResponse(Tz1(r.publicKey, r.publicKeyHash), refresh)
              )

        } yield response).foldM(
          {
            case error: HttpInternalServerError               => InternalServerError(error.asInstanceOf[HttpError])
            case HttpUnauthorizedError | _: HttpDecodingError => Unauthorized(withChallenge("Invalid credentials"))
            case error: HttpError                             => BadRequest(error.asInstanceOf[HttpError])
          },
          Ok(_)
        )

      case req @ POST -> Root =>
        (for {
          decodedRequest: UserCreateRequest <- decodeRequest[UserCreateRequest](req)
//          _ <- UserRoutesCaller
//            .postNewUser(decodedRequest.asJson)  TODO: Fix that soon
            .mapError(error => HttpBadRequestError(error.toString, error.toString))
          response <- UserService
            .createNewUser(
              decodedRequest.email,
              decodedRequest.password,
              decodedRequest.firstName,
              decodedRequest.lastName,
              decodedRequest.nationalIdNumber
            )
            .bimap(
              error => HttpBadRequestError(error.toString, error.toString),
              u => UserResponse(u.sub.value, u.email.value, u.firstName.value, u.lastName.value, u.nationalId.value)
            )

        } yield response).foldM((error: HttpError) => BadRequest(error), u => Ok(u))

      case req @ POST -> Root / "password" / "reset" =>
        (for {
          decodedRequest <- decodeRequest[UserForgotPasswordRequest](req)
          response <- UserService
            .sendForgotPasswordEmail(decodedRequest.email)
            .ignore
        } yield response).foldM((error: HttpError) => BadRequest(error), _ => Accepted())

      case req @ POST -> Root / "password" / "change" =>
        (for {
          decodedRequest <- decodeRequest[UserForgotPasswordChangeRequest](req)
          response <- UserService
            .confirmForgotPassword(decodedRequest.email, decodedRequest.password, decodedRequest.code)
            .mapError(error => HttpBadRequestError(error.msg, error.cause.toString))
        } yield response).foldM((error: HttpError) => BadRequest(error), _ => NoContent())

      case req @ GET -> Root / "tz1" =>
        (for {
          accessToken <- getAccessToken(req)
          response <-
            UserService
              .getUserTz1Keys(accessToken)
              .bimap(
                error =>
                  error.cause match {
                    case UserServerError   => HttpInternalServerError(error.msg, error.cause.toString)
                    case UserNotAuthorized => HttpUnauthorizedError
                    case _                 => HttpBadRequestError(error.msg, error.cause.toString)
                  },
                r => Tz1(r.publicKey, r.publicKeyHash)
              )
        } yield response).foldM(
          {
            case error: HttpInternalServerError               => InternalServerError(error.asInstanceOf[HttpError])
            case HttpUnauthorizedError | _: HttpDecodingError => Unauthorized(withChallenge("Invalid credentials"))
            case error: HttpError                             => BadRequest(error.asInstanceOf[HttpError])
          },
          r => Ok(r)
        )

      case req @ GET -> Root / "burned" =>
        (for {
          accessToken <- getAccessToken(req)
          response <-
            UserService
              .getUserBurnedTz1Keys(accessToken)
              .bimap(
                error =>
                  error match {
                    case _ => HttpBadRequestError(error.toString, error.toString)
                  },
                r => r
              )
        } yield response).foldM(
          {
            case error: HttpInternalServerError               => InternalServerError(error.asInstanceOf[HttpError])
            case HttpUnauthorizedError | _: HttpDecodingError => Unauthorized(withChallenge("Invalid credentials"))
            case error: HttpError                             => BadRequest(error.asInstanceOf[HttpError])
          },
          r => Ok(r.toString)
        )

      case req @ PUT -> Root / "tz1" =>
        (for {
          accessToken    <- getAccessToken(req)
          decodedRequest <- decodeRequest[Tz1](req)
          response <-
            UserService
              .moveUserTz1Keys(accessToken, decodedRequest.publicKey, decodedRequest.publicKeyHash)
              .bimap(
                error =>
                  error.cause match {
                    case DynamoDbEmailNotFoundError => HttpNotFoundError(error.msg, error.cause.toString)
                    case DynamoDbInvalidTz1Error    => HttpConflictError(error.msg, error.cause.toString)
                    case _                          => HttpBadRequestError(error.msg, error.cause.toString)
                  },
                r => r
              )
        } yield response).foldM(
          {
            case error: HttpNotFoundError => NotFound(error)
            case error: HttpConflictError => Conflict(error)
            case error: HttpError         => BadRequest(error)
          },
          r => Ok(r)
        )

      case req @ POST -> Root / "move" =>
        (for {
          accessToken    <- getAccessToken(req)
          decodedRequest <- decodeRequest[Tz1](req)
          response <-
            UserService
              .moveUserTz1Keys(accessToken, decodedRequest.publicKey, decodedRequest.publicKeyHash)
              .bimap(
                error =>
                  error.cause match {
                    case DynamoDbEmailNotFoundError => HttpNotFoundError(error.msg, error.cause.toString)
                    case DynamoDbInvalidTz1Error    => HttpConflictError(error.msg, error.cause.toString)
                    case _                          => HttpBadRequestError(error.msg, error.cause.toString)
                  },
                r => r
              )
        } yield response).foldM(
          {
            case error: HttpNotFoundError => NotFound(error)
            case error: HttpConflictError => Conflict(error)
            case error: HttpError         => BadRequest(error)
          },
          r => Ok(r)
        )

      case req @ PUT -> Root / "send" =>
        (for {
          accessToken    <- getAccessToken(req)
          decodedRequest <- decodeRequest[Tz1](req)
          response <-
            UserService
              .sendEmail(accessToken, UserTz1Keys(decodedRequest.publicKey, decodedRequest.publicKeyHash))
              .mapError(error => HttpBadRequestError(error.msg, error.cause.toString))
        } yield response).foldM((error: HttpError) => BadRequest(error), output => Ok(output))

      case req @ GET -> Root / UUIDVar(uuid) =>
        (for {

          user <-
            UserService
              .getRedis(uuid)
              .mapError(error => HttpBadRequestError(error.msg, error.cause.toString))
          response <- UserService
            .burnUserTz1Keys(user.email, user.tz1)
            .mapError(error => HttpBadRequestError(error.msg, error.cause.toString))
        } yield response).foldM((error: HttpError) => BadRequest(error), output => NoContent())

    }
  }

}
