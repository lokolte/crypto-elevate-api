package io.cryptoelevate.connectors.interfaces.cognito

import java.nio.charset.StandardCharsets
import java.util.Base64

import io.cryptoelevate.Config
import io.cryptoelevate.connectors.models.connectors.CognitoHashCalculator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import zio.{ RLayer, Task, ZIO }

private[connectors] object CognitoHashCalculator {
  trait Service {
    def calculateSecretHash(username: String): Task[String]
  }

  val live: RLayer[Config, CognitoHashCalculator] = (for {
    config    <- ZIO.accessM[Config](_.get.loadConfig())
    awsConfig <- Task.effectTotal(config.aws)
  } yield new Service {

    private val Algorithm = "HmacSHA256"
    private val Charset = StandardCharsets.UTF_8

    private def signingKey(userPoolClientSecret: String) = for {
      bytes     <- Task.effect(userPoolClientSecret.getBytes(StandardCharsets.UTF_8))
      secretKey <- Task.effect(new SecretKeySpec(bytes, Algorithm))
    } yield secretKey

    private def messageAuthenticationCoder(key: String) =
      for {
        macInstance <- Task.effect(Mac.getInstance(Algorithm))
        key         <- signingKey(key)
        _           <- Task.effect(macInstance.init(key))
      } yield macInstance

    override def calculateSecretHash(username: String): Task[String] = for {
      mac           <- messageAuthenticationCoder(awsConfig.cognito.keys.secret)
      usernameBytes <- Task.effect(username.getBytes(Charset))
      _             <- Task.effect(mac.update(usernameBytes))
      macFinal      <- Task.effect(mac.doFinal(awsConfig.cognito.keys.id.getBytes(Charset)))
      hash          <- Task.effect(Base64.getEncoder.encodeToString(macFinal))
    } yield hash
  }).toLayer
}
