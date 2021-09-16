package io.cryptoelevate.connectors.interfaces.cognito

import java.util.UUID

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder
import com.amazonaws.services.cognitoidp.model._
import io.cryptoelevate.Config
import io.cryptoelevate.connectors.models.connectors.{ CognitoHashCalculator, CognitoSdkCaller }
import io.cryptoelevate.connectors.models.{ AccessToken, CognitoUserTokens, RefreshToken, UserTz1Keys }
import io.cryptoelevate.model._
import zio.{ Task, ZIO, ZLayer }

import scala.jdk.CollectionConverters.{ ListHasAsScala, MapHasAsJava }

private[connectors] object CognitoSdkCaller {
  trait Service {
    def createNewUser(
      email: EmailAddress,
      password: Password,
      firstName: FirstName,
      lastName: LastName,
      nationalId: NationalId
    ): Task[User]
    def authUser(email: EmailAddress, password: Password): Task[CognitoUserTokens]
    def refreshToken(userId: UserId, refreshToken: RefreshToken): Task[CognitoUserTokens]
    def sendForgotPasswordEmail(email: EmailAddress): Task[Unit]
    def confirmForgotPassword(email: EmailAddress, password: Password, code: String): Task[Unit]
    def getUserTz1Keys(accessToken: AccessToken): Task[UserTz1Keys]
    def getUserEmailByAccessToken(accessToken: AccessToken): Task[EmailAddress]
    def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): Task[Unit]
  }

  val live: ZLayer[Config with CognitoHashCalculator, Throwable, CognitoSdkCaller] = (for {
    config         <- ZIO.accessM[Config](_.get.loadConfig())
    hashCalculator <- ZIO.service[CognitoHashCalculator.Service]
    awsConfig      <- Task.effectTotal(config.aws)
    keys = awsConfig.awsData
    id = keys.id
    secret = keys.secret
    credentials         <- Task.effect(new BasicAWSCredentials(id, secret))
    credentialsProvider <- Task.effect(new AWSStaticCredentialsProvider(credentials))
    client <- Task.effect(
      AWSCognitoIdentityProviderClientBuilder
        .standard()
        .withCredentials(credentialsProvider)
        .withRegion(awsConfig.region)
        .build()
    )
  } yield new Service {

    private val CustomTz1PublicKeyFieldName = "custom:tz1_public_key"
    private val CustomTz1PublicKeyHashFieldName = "custom:tz1_public_key_hash"
    private val EmailFieldName = "email"
    private val UsernameFieldName = "USERNAME"
    private val FirstnameFieldName = "given_name"
    private val LastnameFieldName = "family_name"
    private val NationalIdNumberFieldName = "name"
    private val SecretHashFieldName = "SECRET_HASH"
    private val PasswordFieldName = "PASSWORD"
    private val RefreshTokenFieldName = "REFRESH_TOKEN"

    private def createAttributeType(name: String, value: String) = Task.effectTotal {
      new AttributeType().withName(name).withValue(value)
    }

    private def prepareAdminInitiateAuthRequest(authParameters: Map[String, String]): AdminInitiateAuthRequest =
      (new AdminInitiateAuthRequest)
        .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
        .withClientId(awsConfig.cognito.keys.id)
        .withUserPoolId(awsConfig.poolId)
        .withAuthParameters(authParameters.asJava)

    private def prepareAuthRequest(email: EmailAddress, password: Password) =
      for {
        hash <- hashCalculator.calculateSecretHash(email.value)
        request <- Task.effectTotal(
          prepareAdminInitiateAuthRequest(
            Map(UsernameFieldName -> email.value, SecretHashFieldName -> hash, PasswordFieldName -> password.value)
          )
        )
      } yield request

    private def prepareAdminInitiateRefreshTokenRequest(authParameters: Map[String, String]): AdminInitiateAuthRequest =
      (new AdminInitiateAuthRequest)
        .withAuthFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
        .withClientId(awsConfig.cognito.keys.id)
        .withUserPoolId(awsConfig.poolId)
        .withAuthParameters(authParameters.asJava)

    private def prepareAuthRefreshTokenRequest(userId: UserId, token: RefreshToken) = for {
      hash <- hashCalculator.calculateSecretHash(userId.value.toString)
      request <- Task.effectTotal(
        prepareAdminInitiateRefreshTokenRequest(
          Map(
            UsernameFieldName -> userId.value.toString,
            SecretHashFieldName -> hash,
            RefreshTokenFieldName -> token.value
          )
        )
      )
    } yield request

    private def makeAdminInitiateAuthRequest(email: EmailAddress, password: Password) = for {
      request  <- prepareAuthRequest(email, password)
      response <- Task.effect(client.adminInitiateAuth(request))
    } yield response

    private def authResultToCognitoUserTokens(result: AdminInitiateAuthResult) =
      for {
        authRes <- Task.effectTotal(result.getAuthenticationResult)
        tokens <- Task.effectTotal(
          CognitoUserTokens.fromRaw(authRes.getAccessToken, authRes.getRefreshToken, authRes.getExpiresIn)
        )
      } yield tokens

    private def checkNationalIdUniqueness(nationalId: NationalId) = for {
      request <- Task.effectTotal {
        val request = new ListUsersRequest()
        request.setFilter(s"""name = "${nationalId.value}"""")
        request.withUserPoolId(awsConfig.poolId)
        request
      }
      result <- Task.effect(client.listUsers(request))
      _ <-
        Task.fail(new InvalidParameterException("NationalId is already taken.")).unless(result.getUsers.isEmpty)
    } yield ()

    override def createNewUser(
      email: EmailAddress,
      password: Password,
      firstName: FirstName,
      lastName: LastName,
      nationalId: NationalId
    ): Task[User] = for {
      _                    <- checkNationalIdUniqueness(nationalId)
      attrEmail            <- createAttributeType(EmailFieldName, email.value)
      attrFirstName        <- createAttributeType(FirstnameFieldName, firstName.value)
      attrLastName         <- createAttributeType(LastnameFieldName, lastName.value)
      attrNationalIdNumber <- createAttributeType(NationalIdNumberFieldName, nationalId.value)
      hash                 <- hashCalculator.calculateSecretHash(email.value)
      signUpRequest <- Task.effectTotal {
        val req = new SignUpRequest()
        req.withSecretHash(hash)
        req.withClientId(awsConfig.cognito.keys.id)
        req.withUsername(email.value)
        req.withPassword(password.value)
        req.withUserAttributes(attrEmail, attrFirstName, attrLastName, attrNationalIdNumber)
        req
      }
      response <- Task.effect(client.signUp(signUpRequest))
      sub      <- Task.effect(UUID.fromString(response.getUserSub))
    } yield User(sub = UserId(sub), email = email, firstName = firstName, lastName = lastName, nationalId = nationalId)

    override def authUser(email: EmailAddress, password: Password): Task[CognitoUserTokens] =
      for {
        response <- makeAdminInitiateAuthRequest(email, password)
        tokens   <- authResultToCognitoUserTokens(response)
      } yield tokens

    override def sendForgotPasswordEmail(email: EmailAddress): Task[Unit] =
      for {
        request <- Task.effectTotal {
          val request = new AdminResetUserPasswordRequest()
          request.withUserPoolId(awsConfig.poolId)
          request.withUsername(email.value)
          request.withRequestCredentialsProvider(credentialsProvider)
          request
        }
        _ <- Task.effect(client.adminResetUserPassword(request))
      } yield ()

    override def confirmForgotPassword(email: EmailAddress, newPassword: Password, code: String): Task[Unit] =
      for {
        hash <- hashCalculator.calculateSecretHash(email.value)
        request <- Task.effectTotal {
          new ConfirmForgotPasswordRequest()
            .withSecretHash(hash)
            .withClientId(awsConfig.cognito.keys.id)
            .withConfirmationCode(code)
            .withPassword(newPassword.value)
            .withUsername(email.value)
        }
        _ <- Task.effect(client.confirmForgotPassword(request))
      } yield ()

    override def updateUserTz1Keys(accessToken: AccessToken, keys: UserTz1Keys): Task[Unit] =
      for {
        attrPublicKey     <- createAttributeType(CustomTz1PublicKeyFieldName, keys.publicKey)
        attrPublicKeyHash <- createAttributeType(CustomTz1PublicKeyHashFieldName, keys.publicKeyHash)
        request <- Task.effectTotal(
          new UpdateUserAttributesRequest()
            .withAccessToken(accessToken.value)
            .withUserAttributes(attrPublicKey, attrPublicKeyHash)
        )
        _ <- Task.effect(client.updateUserAttributes(request))
      } yield ()

    override def getUserTz1Keys(accessToken: AccessToken): Task[UserTz1Keys] = for {
      request <- Task.effectTotal(new GetUserRequest().withAccessToken(accessToken.value))
      tz1Fields = List(CustomTz1PublicKeyFieldName, CustomTz1PublicKeyHashFieldName)
      attributes <- Task.effect(
        client.getUser(request).getUserAttributes.asScala.toList.filter(attr => tz1Fields.contains(attr.getName))
      )
      publicKey     <- findAttribute(CustomTz1PublicKeyFieldName, attributes)
      publicKeyHash <- findAttribute(CustomTz1PublicKeyHashFieldName, attributes)
    } yield UserTz1Keys(publicKey = publicKey, publicKeyHash = publicKeyHash)

    override def getUserEmailByAccessToken(accessToken: AccessToken): Task[EmailAddress] = for {
      request <- Task.effectTotal(new GetUserRequest().withAccessToken(accessToken.value))
      tz1Fields = List(EmailFieldName)
      attributes <- Task.effect(
        client.getUser(request).getUserAttributes.asScala.toList.filter(attr => tz1Fields.contains(attr.getName))
      )
      email <- findAttribute(EmailFieldName, attributes)
    } yield EmailAddress(email)

    private def findAttribute(name: String, attributes: Seq[AttributeType]) = Task.fromEither {
      attributes.collectFirst { case attr if attr.getName == name => attr.getValue }
        .toRight(new ResourceNotFoundException(s"$name not found in attributes"))
    }

    override def refreshToken(userId: UserId, refreshToken: RefreshToken): Task[CognitoUserTokens] = for {
      request  <- prepareAuthRefreshTokenRequest(userId, refreshToken)
      response <- Task.effect(client.adminInitiateAuth(request))
      tokens   <- authResultToCognitoUserTokens(response)
    } yield tokens
  }).toLayer

}
