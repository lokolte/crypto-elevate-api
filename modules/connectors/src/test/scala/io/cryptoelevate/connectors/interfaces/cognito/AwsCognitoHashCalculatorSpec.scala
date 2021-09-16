package io.cryptoelevate.connectors.interfaces.cognito

import io.cryptoelevate.Config
import io.cryptoelevate.Config.{AwsConfig, AwsKeys, CEConfig, CognitoConfig, DynamoConfig, HttpConfig, LambdaConfig, RedisConfig, SesConfig}
import io.cryptoelevate.connectors.models.connectors.CognitoHashCalculator
import zio.test.Assertion.equalTo
import zio.{Task, ZIO, ZLayer}
import zio.test._

object AwsCognitoHashCalculatorSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("cognito hash calculator spec")(testM("calculates secret hash properly") {
      val resp = ZIO.accessM[CognitoHashCalculator](_.get.calculateSecretHash("john@doe.io")).provideLayer(env)
      assertM(resp)(equalTo("MPes6MCbb9oZNDZDTpNlwG96sr65NiXkyFi8V7h2HCY="))
    })

  private val configEnv = ZLayer.succeed(new Config.Service {
    override def loadConfig(): Task[Config.CEConfig] = Task.succeed {
      CEConfig(
        aws = AwsConfig(
          awsData = AwsKeys("1111", "2222"),
          lambda = LambdaConfig(functionName = "someTests"),
          cognito = CognitoConfig(keys = AwsKeys("3333", "44444")),
          dynamo = DynamoConfig(tableName = "SomeTable"),
          ses = SesConfig("po-22", "localhost", "example@ww.io"),
          redis = RedisConfig("url", 231),
          region = "xy-qwerty-1",
          poolId = "1234567890"
        ),
        http = HttpConfig(host = "0.0.0.0", port = 8080)
      )
    }
  })

  private val env = configEnv >>> CognitoHashCalculator.live
}
