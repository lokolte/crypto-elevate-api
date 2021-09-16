//package io.cryptoelevate.http
//
//import io.circe.literal._
//import io.cryptoelevate.connectors.intefaces.cognito.CognitoConnector
//import io.cryptoelevate.connectors.intefaces.dynamo.AwsDynamoConnector
//import io.cryptoelevate.connectors.models._
//import io.cryptoelevate.connectors.intefaces.ses.SesConnector
//import io.cryptoelevate.dataservice.{
//  DummyAwsDynamoConnector,
//  DummyAwsSesConnector,
//  DummyCognitoConnector,
//  UserService
//}
//import io.cryptoelevate.http.common.HTTPSpec
//import io.cryptoelevate.http.routes.UserRoutesService
//import org.http4s.circe._
//import org.http4s.implicits._
//import org.http4s._
//import zio._
//import zio.interop.catz._
//import zio.test._
//import zio.test.environment.TestEnvironment
//
//object UserRoutesSpec extends DefaultRunnableSpec with HTTPSpec {
//  type UserTask[A] = RIO[UserService, A]
//
//  private val app = UserRoutesService.routes[UserService]().orNotFound
//
//  private def prepareEnv(
//    cognitoLayer: CognitoConnector.Service = DummyCognitoConnector(),
//    sesLayer: SesConnector.Service = DummyAwsSesConnector(),
//    dynamoLayer: AwsDynamoConnector.Service = DummyAwsDynamoConnector()
//  ) =
//    ZLayer.succeed(cognitoLayer) ++ ZLayer.succeed(dynamoLayer) ++ ZLayer.succeed(sesLayer) >>> UserService.live
//
//  private val dummyLayer = prepareEnv()
//
//  override def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
//    suite("User routes")(createUserSuite, authUserSuite, passwordReset, confirmForgotPassword)
//
//  private val createUserSuite = suite("Create user")(
//    testM("create new user successfully") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail@atx.xy", "national_id_number": "112233", "password": "!QAZ@WSXcde3vfr4"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.Ok,
//        Some(json"""{
//            "sub": "13fac0b5-f0c0-4de4-a2ca-97209949591f",
//            "email": "john@doe.io",
//            "first_name": "John",
//            "last_name": "Doe",
//            "national_id": "11-22-33-44-55"
//          }""")
//      ).provideLayer(dummyLayer)
//    },
//    testM("fails create new user, invalid password") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail@atx.xy", "national_id_number": "112233", "password": "!QAZ@WSXcde3vfr4"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{
//            "msg": "Password failed to satisfy constraint",
//            "cause": "UserGenericError"
//          }""")
//      ).provideLayer(
//        prepareEnv(DummyCognitoConnector.createFailingCreateNewUser(InvalidPasswordNotSatisfyConstraintError))
//      )
//    },
//    testM("fails create new user, user already exists") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail@atx.xy", "national_id_number": "112233", "password": "!QAZ@WSXcde3vfr4"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{
//            "msg": "Username already exists",
//            "cause": "UserGenericError"
//          }""")
//      ).provideLayer(prepareEnv(DummyCognitoConnector.createFailingCreateNewUser(UsernameExistsError)))
//    },
//    testM("fails create new user, nationalId already taken") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail@atx.xy", "national_id_number": "112233", "password": "!QAZ@WSXcde3vfr4"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{
//            "msg": "NationalId is already taken",
//            "cause": "UserGenericError"
//          }""")
//      ).provideLayer(prepareEnv(DummyCognitoConnector.createFailingCreateNewUser(InvalidNationalIdNotUniqueError)))
//    },
//    testM("fails create new user, unknown error") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail@atx.xy", "national_id_number": "112233", "password": "!QAZ@WSXcde3vfr4"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{
//            "msg": "Unhandled error had happen",
//            "cause": "UserServerError"
//          }""")
//      ).provideLayer(prepareEnv(DummyCognitoConnector.createFailingCreateNewUser(AwsGenericError)))
//    },
//    testM("fails if invalid email field is provided") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail[at]atx.xy", "national_id_number": "112233", "password": "!QAZ@WSXcde3vfr4"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{
//            "msg": "Not valid email format should be: name@domain.com",
//            "cause": "Decoding error"
//          }""")
//      ).provideLayer(prepareEnv(DummyCognitoConnector.createFailingCreateNewUser(AwsGenericError)))
//    },
//    testM("fails if password does not match criteria") {
//      val req = request[UserTask](Method.POST, "/")
//        .withEntity(
//          json"""{"first_name": "Test","last_name": "Test", "email": "mail@atx.xy", "national_id_number": "112233", "password": "simple"}"""
//        )
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{
//            "msg": "Not valid password should be: min 8 chars, contain at least: 1 letter, upper and lower case, one special character",
//            "cause": "Decoding error"
//          }""")
//      ).provideLayer(prepareEnv(DummyCognitoConnector.createFailingCreateNewUser(AwsGenericError)))
//    }
//  )
//
//  private val authUserSuite = suite("Auth user")(
//    testM("Create user successfully") {
//      val req = request[UserTask](Method.POST, "/signin")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "!QAZ@WSXcde3vfr4"}""")
//      checkRequest(
//        app.run(req),
//        Status.Ok,
//        Some(json"""{"access_token": "abc_123","refresh_token": "qwe_987","expires_in": 999}""")
//      ).provideLayer(dummyLayer)
//    },
//    testM("Fails on invalid credentials") {
//      val req = request[UserTask](Method.POST, "/signin")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "!QAZ@WSXcde3vfr4"}""")
//      checkRequest(app.run(req), Status.Unauthorized, None)
//        .provideLayer(prepareEnv(DummyCognitoConnector.createFailingAuthUser(InvalidUsernameOrPasswordError)))
//    },
//    testM("Fails on invalid email format") {
//      val req = request[UserTask](Method.POST, "/signin")
//        .withEntity(json"""{"email": "mail[at]atx.xy", "password": "!QAZ@WSXcde3vfr4"}""")
//      checkRequest(app.run(req), Status.Unauthorized, None)
//        .provideLayer(prepareEnv(DummyCognitoConnector.createFailingAuthUser(InvalidUsernameOrPasswordError)))
//    },
//    testM("Fails if password does not match criteria") {
//      val req = request[UserTask](Method.POST, "/signin")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "simple"}""")
//      checkRequest(app.run(req), Status.Unauthorized, None)
//        .provideLayer(prepareEnv(DummyCognitoConnector.createFailingAuthUser(InvalidUsernameOrPasswordError)))
//    }
//  )
//
//  private val passwordReset = suite("Password reset")(
//    testM("Send password reset request") {
//      val req = request[UserTask](Method.POST, "/password/reset")
//        .withEntity(json"""{"email": "mail@atx.xy"}""")
//      checkRequest(app.run(req), Status.Accepted, None).provideLayer(dummyLayer)
//    },
//    testM("Fails on unknown error") {
//      val req = request[UserTask](Method.POST, "/password/reset")
//        .withEntity(json"""{"email": "mail@atx.xy"}""")
//      checkRequest(app.run(req), Status.Accepted, None)
//        .provideLayer(prepareEnv(DummyCognitoConnector.createFailingSendForgotPasswordEmail(AwsGenericError)))
//    },
//    testM("Fails on invalid email format") {
//      val req = request[UserTask](Method.POST, "/password/reset")
//        .withEntity(json"""{"email": "mail[at]atx.xy"}""")
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{"msg":"Not valid email format should be: name@domain.com","cause":"Decoding error"}""")
//      ).provideLayer(prepareEnv(DummyCognitoConnector.createFailingAuthUser(InvalidUsernameOrPasswordError)))
//    }
//  )
//
//  private val confirmForgotPassword = suite("Confirm forgot password")(
//    testM("Confirm new password successfully") {
//      val req = request[UserTask](Method.POST, "/password/change")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "Test12345!@", "code": "442278"}""")
//      checkRequest(app.run(req), Status.NoContent, None).provideLayer(dummyLayer)
//    },
//    testM("Fails on invalid password ") {
//      val req = request[UserTask](Method.POST, "/password/change")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "Test12345!@", "code": "442278"}""")
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{"msg":"Password failed to satisfy constraint","cause":"UserGenericError"}""")
//      )
//        .provideLayer(
//          prepareEnv(DummyCognitoConnector.createFailingConfirmForgotPassword(InvalidPasswordNotSatisfyConstraintError))
//        )
//    },
//    testM("Fails on invalid code") {
//      val req = request[UserTask](Method.POST, "/password/change")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "Test12345!@", "code": "442278"}""")
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{"msg":"Incorrect validation code","cause":"UserGenericError"}""")
//      )
//        .provideLayer(
//          prepareEnv(DummyCognitoConnector.createFailingConfirmForgotPassword(InvalidPasswordChangeValidationCodeError))
//        )
//    },
//    testM("Fails on code expired") {
//      val req = request[UserTask](Method.POST, "/password/change")
//        .withEntity(json"""{"email": "mail@atx.xy", "password": "Test12345!@", "code": "442278"}""")
//      checkRequest(
//        app.run(req),
//        Status.BadRequest,
//        Some(json"""{"msg":"Forgot password code expired","cause":"UserGenericError"}""")
//      )
//        .provideLayer(
//          prepareEnv(DummyCognitoConnector.createFailingConfirmForgotPassword(ForgotPasswordCodeExpiredError))
//        )
//    }
//  )
//
//}
