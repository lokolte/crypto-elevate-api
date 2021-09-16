package io.cryptoelevate

import zio.Has

package object dataservice {
  type UserService = Has[UserService.Service]

  sealed trait UserErrorCause
  case object UserNotAuthorized extends UserErrorCause
  case object UserGenericError extends UserErrorCause
  case object UserServerError extends UserErrorCause
  case object DynamoDbEmailNotFoundError extends UserErrorCause
  case object DynamoDbInvalidTz1Error extends UserErrorCause
  case object DynamoDbGenericError extends UserErrorCause
  // TODO: define the correct error types
  case object LambdaInvocationServiceError extends UserErrorCause
  case object LambdaInvocationOtherError extends UserErrorCause

  final case class UserError(msg: String, cause: UserErrorCause)
}
