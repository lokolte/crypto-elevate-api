package io.cryptoelevate.connectors

package object errors {

  trait Error {
    val msg: String
  }

  //Common Errors
  sealed trait CommonError extends DynamoError with RedisError with CognitoError with SesError

  case object UsernameExistsError extends CommonError {
    override val msg: String = "Username already exists"
  }

  case class GenericError(error: String) extends CommonError {
    override val msg: String = "Unhandled error had happen: " + error
  }

  //Dynamo Errors
  sealed trait DynamoError extends Error

  case object DynamoInvalidEmailNotUniqueError extends DynamoError {
    override val msg: String = "Email is already taken"
  }

  case object DynamoEmailExistsError extends DynamoError {
    override val msg: String = "Email already exists"
  }

  case object DynamoEmailNotFoundError extends DynamoError {
    override val msg: String = "Email not found"
  }

  case object DynamoTZ1KeyNotExistError extends DynamoError {
    override val msg: String = "TZ1Key does not exists as inactive or was already burned"
  }

  case object DynamoInvalidTZ1KeyError extends DynamoError {
    override val msg: String = "TZ1Key is already exist or is inactive or burned"
  }

  case class DynamoGenericError(error: String) extends DynamoError {
    override val msg: String = "Unhandled error had happen: " + error
  }

  //Cognito Errors
  sealed trait CognitoError extends Error

  case object CognitoInvalidUsernameOrPasswordError extends CognitoError {
    override val msg: String = "Incorrect username or password"
  }

  case object CognitoPasswordResetRequiredError extends CognitoError {
    override val msg: String = " Password reset required for the user"
  }

  case object CognitoInvalidPasswordNotSatisfyConstraintError extends CognitoError {
    override val msg: String = "Password failed to satisfy constraint"
  }

  case object CognitoInvalidNationalIdNotUniqueError extends CognitoError {
    override val msg: String = "NationalId is already taken"
  }

  case object CognitoUserNotConfirmedError extends CognitoError {
    override val msg: String = "User is not confirmed"
  }

  case object CognitoInvalidPasswordChangeValidationCodeError extends CognitoError {
    override val msg: String = "Incorrect validation code"
  }

  case object CognitoForgotPasswordCodeExpiredError extends CognitoError {
    override val msg: String = "Forgot password code expired"
  }

  //Redis Errors
  sealed trait RedisError extends Error

  case object RedisUserNotFound extends RedisError {
    override val msg: String = "User not found"
  }

  //SES Errors
  sealed trait SesError extends Error
  case object SesConnectionError extends SesError { //TODO: In the future make those types better
    override val msg: String = "Ses couldn't make connection"
  }
  case object SesUserNotAuthorisedToSendEmails extends SesError {
    override val msg: String = "User is not authorised to send emails"
  }

  //Lambda Errors
  sealed trait LambdaError extends Error

  case object LambdaServiceError extends LambdaError {
    override val msg: String = "There was an error in lambda execution"
  }

  case class LambdaGenericError(error: String) extends LambdaError {
    override val msg: String = "Unhandled error had happen: " + error
  }
}
