package io.cryptoelevate.dataservice

import java.util.UUID

import com.amazonaws.services.simpleemail.model.SendEmailResult
import io.cryptoelevate.DummyCognitoData
import io.cryptoelevate.connectors.interfaces.cognito.common.DummyData
import io.cryptoelevate.connectors.{ errors, models }
import io.cryptoelevate.connectors.interfaces.redis.RedisConnector
import io.cryptoelevate.connectors.interfaces.ses.SesConnector
import zio.{ IO, Task }

object DummyRedisConnector {

  def apply(): RedisConnector.Service = new DummyRedisConnector

}

class DummyRedisConnector extends RedisConnector.Service with DummyCognitoData with DummyData {
  //TODO: Implement that
  override def set(user: models.RedisUser): IO[errors.RedisError, Boolean] = ???

  override def get(uid: UUID): IO[errors.RedisError, models.RedisUser] = ???
}
