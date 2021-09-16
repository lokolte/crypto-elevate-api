package io.cryptoelevate.http

import io.cryptoelevate.{Config, Logger}
import io.cryptoelevate.connectors.interfaces.cognito.CognitoConnector
import io.cryptoelevate.connectors.interfaces.dynamo.DynamoConnector
import io.cryptoelevate.connectors.interfaces.lambda.LambdaConnector
import io.cryptoelevate.connectors.interfaces.redis.RedisConnector
import io.cryptoelevate.connectors.interfaces.ses.SesConnector
import io.cryptoelevate.dataservice.UserService
import zio.ZLayer
import zio.logging.Logging

object Layers {

  type AppEnv = UserService with Config with Logging

  val userServiceLayer =
    SesConnector.live ++ DynamoConnector.live ++ CognitoConnector.live ++ RedisConnector.live ++ LambdaConnector.live >>> UserService.live

  val appLayer: ZLayer[Any, Throwable, AppEnv] =
    userServiceLayer ++ Config.live ++ Logger.liveEnv("ce-http")
}
