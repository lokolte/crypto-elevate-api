package io.cryptoelevate.connectors.interfaces.redis

import io.cryptoelevate.connectors.models.{ RedisUser, SendingEmail }
import zio.blocking.Blocking
import zio.{ Has, IO, Task, ZIO, ZLayer }
import com.redis._
import io.cryptoelevate.connectors.models.connectors.RedisSdkCaller
import com.redis.serialization.Parse.Implicits.{ parseString, _ }
import serialization._
private[connectors] object RedisSdkCaller {

  trait Service {

    def set(user: RedisUser): Task[Boolean]
    def get(uuid: String): Task[Option[Map[String, String]]]
  }

  val live: ZLayer[Has[Blocking.Service] with Has[RedisClient], Throwable, RedisSdkCaller] = (for {
    zioBlocking: Blocking.Service <- ZIO.service[Blocking.Service]
    client                        <- ZIO.service[RedisClient]
  } yield new RedisSdkCaller.Service {

    override def set(user: RedisUser): Task[Boolean] =
      zioBlocking.effectBlocking(
        client.hmset(
          user.uuid,
          Map("email" -> user.email.value, "publicKey" -> user.tz1.publicKey, "publicKeyHash" -> user.tz1.publicKeyHash)
        )
      )

    override def get(uuid: String): Task[Option[Map[String, String]]] =
      zioBlocking.effectBlocking(client.hmget[String, String](uuid, "email", "publicKey", "publicKeyHash"))

  }).toLayer
}
