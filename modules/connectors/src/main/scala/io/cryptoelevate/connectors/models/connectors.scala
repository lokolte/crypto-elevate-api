package io.cryptoelevate.connectors.models

import io.cryptoelevate.connectors.interfaces.cognito.{ CognitoConnector, CognitoHashCalculator, CognitoSdkCaller }
import io.cryptoelevate.connectors.interfaces.dynamo.{ DynamoConnector, DynamoSdkCaller }
import io.cryptoelevate.connectors.interfaces.redis.{ RedisConnector, RedisSdkCaller }
import io.cryptoelevate.connectors.interfaces.ses.{ SesConnector, SesSdkCaller }
import io.cryptoelevate.connectors.interfaces.lambda.{LambdaConnector, LambdaSdkCaller}
import zio.Has

package object connectors {
  type CognitoConnector = Has[CognitoConnector.Service]
  type CognitoHashCalculator = Has[CognitoHashCalculator.Service]
  type CognitoSdkCaller = Has[CognitoSdkCaller.Service]
  type DynamoConnector = Has[DynamoConnector.Service]
  type DynamoSdkCaller = Has[DynamoSdkCaller.Service]
  type SesSdkCaller = Has[SesSdkCaller.Service]
  type SesConnector = Has[SesConnector.Service]
  type RedisSdkCaller = Has[RedisSdkCaller.Service]
  type RedisConnector = Has[RedisConnector.Service]
  type LambdaSdkCaller = Has[LambdaSdkCaller.Service]
  type LambdaConnector = Has[LambdaConnector.Service]
}
