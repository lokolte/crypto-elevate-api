package io.cryptoelevate

import pureconfig._
import zio._
import pureconfig.generic.auto._

object Config {

  final case class AwsKeys(id: String, secret: String)
  final case class LambdaConfig(functionName: String)
  final case class CognitoConfig(keys: AwsKeys)
  final case class DynamoConfig(tableName: String)
  final case class RedisConfig(url: String, port: Int)
  final case class SesConfig(url: String, prefix: String, sendingEmailAddress: String)
  final case class AwsConfig(
    awsData: AwsKeys,
    lambda: LambdaConfig,
    cognito: CognitoConfig,
    dynamo: DynamoConfig,
    ses: SesConfig,
    redis: RedisConfig,
    region: String,
    poolId: String
  )

  final case class HttpConfig(host: String, port: Int)
  final case class CEConfig(aws: AwsConfig, http: HttpConfig)
  trait Service {
    def loadConfig(): Task[CEConfig]
  }
  val live: ULayer[Has[Service]] = ZLayer.succeed(() => Task.effect(ConfigSource.default.loadOrThrow[CEConfig]))

  def loadConfig(): ZIO[Config, Throwable, CEConfig] =
    ZIO.accessM(_.get.loadConfig())
}
