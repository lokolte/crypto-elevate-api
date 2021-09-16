package io.cryptoelevate.dataservice

import com.amazonaws.services.simpleemail.model.SendEmailResult
import io.cryptoelevate.DummyCognitoData
import io.cryptoelevate.connectors.interfaces.cognito.common.DummyData
import io.cryptoelevate.connectors.errors
import io.cryptoelevate.connectors.interfaces.cognito.CognitoConnector
import io.cryptoelevate.connectors.models._
import io.cryptoelevate.connectors.interfaces.ses.SesConnector
import io.cryptoelevate.model._
import zio.{ IO, Task }

object DummyAwsSesConnector {

  def apply(): SesConnector.Service = new DummyAwsSesConnector

}
class DummyAwsSesConnector extends SesConnector.Service with DummyCognitoData with DummyData {
//TODO: Implement that send
  override def send(email: String, uuid: String): IO[errors.SesError, SendEmailResult] = ???
}
