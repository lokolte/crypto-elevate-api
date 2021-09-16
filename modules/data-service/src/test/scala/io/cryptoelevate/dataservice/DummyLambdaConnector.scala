package io.cryptoelevate.dataservice

import io.cryptoelevate.connectors.errors.LambdaError
import io.cryptoelevate.connectors.interfaces.lambda.LambdaConnector
import io.cryptoelevate.connectors.models._
import zio.IO

object DummyLambdaConnector {
  def apply(): LambdaConnector.Service = new DummyLambdaConnector
}

class DummyLambdaConnector extends LambdaConnector.Service {

  final val dummyBurnResponse: LambdaResponse =
    LambdaResponse( //TODO: Move this to DummyData package in tests
    )

  def burn(oldAddress: UserTz1Keys, newAddress: UserTz1Keys): IO[LambdaError, LambdaResponse] = IO.succeed(dummyBurnResponse)
}
