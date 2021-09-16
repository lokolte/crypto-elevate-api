package io.cryptoelevate.connectors.interfaces.lambda

import io.cryptoelevate.Config
import io.cryptoelevate.connectors.models._
import io.cryptoelevate.connectors.models.UserTz1Keys
import io.cryptoelevate.connectors.models.connectors.LambdaSdkCaller
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.{InvokeRequest, InvokeResult}
import io.circe.DecodingFailure
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import zio._
import zio.blocking.Blocking
import zio.{Has, Task, ZIO, ZLayer}

private[connectors] object LambdaSdkCaller {

  trait Service {
    def burn(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): Task[LambdaResponse]
  }

  val live: ZLayer[Config with Has[Blocking.Service] with Has[AWSLambda], Throwable, LambdaSdkCaller] = (for {
    zioBlocking: Blocking.Service <- ZIO.service[Blocking.Service]
    client: AWSLambda             <- ZIO.service[AWSLambda]
    config                        <- ZIO.accessM[Config](_.get.loadConfig())
    awsConfig                     <- Task.effectTotal(config.aws)
    lambda = awsConfig.lambda
  } yield new LambdaSdkCaller.Service {

    private def invokeBurnLambda(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): Task[InvokeResult] = {
      for{
        invokeRequest <- Task.effectTotal(
          new InvokeRequest()
            .withFunctionName(lambda.functionName)
            .withPayload(
              LambdaRequest(Body(oldAddress.publicKey, newAddress.publicKey)).asJson.toString()
            )
        )
        invokeResult <- zioBlocking.effectBlocking(client.invoke(invokeRequest))
      } yield invokeResult
    }

    override def burn(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): Task[LambdaResponse] =
      for {
        invokeResult <- invokeBurnLambda(oldAddress, newAddress)
        result <-
          decode[LambdaResponse](new String(invokeResult.getPayload().array()).asJson.toString()) match {
            case Left(DecodingFailure(_, _)) => IO.fail(new IllegalArgumentException(new String(invokeResult.getPayload().array()).asJson.toString())) // convert to the error json from the lambda
            case Right(burnResponse) => IO.succeed(burnResponse)
          }
      } yield result
  }).toLayer
}
