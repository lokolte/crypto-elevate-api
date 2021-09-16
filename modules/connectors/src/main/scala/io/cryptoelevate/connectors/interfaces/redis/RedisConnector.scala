package io.cryptoelevate.connectors.interfaces.redis

import java.util.UUID

import com.redis.RedisClient
import io.cryptoelevate.connectors.errors.{ GenericError, RedisError, RedisUserNotFound }
import io.cryptoelevate.connectors.models.connectors.{ RedisConnector, RedisSdkCaller }
import io.cryptoelevate.connectors.models.{ RedisUser, UserTz1Keys }
import io.cryptoelevate.model.EmailAddress
import io.cryptoelevate.{ Config, Logger }
import zio.blocking.Blocking
import zio.logging.Logging
import zio.{ IO, _ }

object RedisConnector {

  trait Service {
    def set(user: RedisUser): IO[RedisError, Boolean]
    def get(uuid: UUID): IO[RedisError, RedisUser]
  }

  private val configuredRedisClient: RLayer[Config, Has[RedisClient]] =
    (for {
      config    <- ZIO.accessM[Config](_.get.loadConfig())
      awsConfig <- Task.effectTotal(config.aws.redis)
      port = awsConfig.port
      url = awsConfig.url //TODO: add keys to .env

      client: RedisClient = new RedisClient(url, port)

    } yield client).toLayer.orDie

  lazy val live: TaskLayer[RedisConnector] =
    (Blocking.live ++ (Config.live >>> configuredRedisClient)) >>> RedisSdkCaller.live ++ Logger
      .liveEnv("redis") >>> liveConnectorLayer

  private[connectors] val liveConnectorLayer: ZLayer[RedisSdkCaller with Logging, Throwable, RedisConnector] =
    (for {

      sdkCaller <- ZIO.service[RedisSdkCaller.Service]
      logLayer  <- ZIO.identity[Logging]
    } yield new Service {
      private val log = logLayer.get

      override def set(user: RedisUser): IO[RedisError, Boolean] = for {

        ifUserWasSet <- sdkCaller.set(user).mapError(error => GenericError(error.toString))
        _            <- log.debug("Redis user " + user + " was set")
      } yield ifUserWasSet

      override def get(uuid: UUID): IO[RedisError, RedisUser] = for {
        mappedValues <- sdkCaller
          .get(uuid.toString)
          .mapError(error => GenericError(error.toString))
          .tapError(error => log.error("Redis have returned error: " + error))
          .someOrFail(RedisUserNotFound)

        redisUser <- ZIO.effectTotal(
          RedisUser(
            uuid,
            EmailAddress(mappedValues.getOrElse("email", "notFound")),
            UserTz1Keys(
              mappedValues.getOrElse("publicKey", "notFound"),
              mappedValues.getOrElse("publicKeyHash", "notFound")
            )
          )
        )
      } yield redisUser

    }).toLayer

  def set(user: RedisUser): ZIO[RedisConnector, RedisError, Boolean] =
    ZIO.accessM(_.get.set(user))

  def get(uuid: UUID): ZIO[RedisConnector, RedisError, RedisUser] =
    ZIO.accessM(_.get.get(uuid))

}
