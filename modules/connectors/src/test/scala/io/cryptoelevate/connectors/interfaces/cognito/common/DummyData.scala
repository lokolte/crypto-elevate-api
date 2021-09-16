package io.cryptoelevate.connectors.interfaces.cognito.common

import io.cryptoelevate.connectors.models._
import io.cryptoelevate.model.EmailAddress

trait DummyData {

  final val dummyAuthTokens: CognitoUserTokens = CognitoUserTokens(
    accessToken = AccessToken("abc_123"),
    refreshToken = RefreshToken("qwe_987"),
    expiresIn = TokenExpiration(999)
  )

  final val dummyAccessToken: AccessToken = dummyAuthTokens.accessToken
  final val dummyEmail: EmailAddress = EmailAddress("dummyData")

  final val dummyTz1Keys: UserTz1Keys = UserTz1Keys("abc_123", "987_123")

}
