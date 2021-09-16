package io.cryptoelevate.http.routes

import org.http4s.Challenge
import org.http4s.headers.`WWW-Authenticate`

private[routes] trait UnauthorizedResponseSupport {
  private final val SCHEME = "Token"
  private final val REALM = "Re-login with proper credentials to get a valid token"

  protected def withChallenge(err: String): `WWW-Authenticate` = {
    val params = Map("error" -> "invalid_token", "error_description" -> err)
    `WWW-Authenticate`(Challenge(SCHEME, REALM, params))
  }
}
