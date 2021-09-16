package com.example

import com.typesafe.config.{ Config, ConfigFactory }
import org.scanamo.{ LocalDynamoDB, Scanamo }
import zio.blocking.Blocking
import zio.duration.durationInt
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{ Has, Layer, TaskLayer, ULayer, ZLayer }
import org.scanamo.syntax._
import zio.CanFail.canFailAmbiguous1
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{ Has, Layer, ULayer, ZLayer }
import zio.blocking.Blocking
import zio.duration.durationInt

import scala.jdk.CollectionConverters.MapHasAsJava
object ITSpec {

  type ITEnv = TestEnvironment with Logging with Has[Scanamo]
  abstract class ITSpec(table: Option[String] = None) extends RunnableSpec[ITEnv, Any] {
    type ITSpec = ZSpec[ITEnv, Any]

    override def aspects: List[TestAspect[Nothing, ITEnv, Nothing, Any]] =
      List(TestAspect.timeout(60.seconds))

    override def runner: TestRunner[ITEnv, Any] =
      TestRunner(TestExecutor.default(itLayer))

    val logging: ULayer[Logging] = Slf4jLogger.make { (context, message) =>
      val logFormat = "[correlation-id = %s] %s"
      val correlationId = LogAnnotation.CorrelationId.render(context.get(LogAnnotation.CorrelationId))
      logFormat.format(correlationId, message)
    }
    val client: ULayer[Has[Scanamo]] = ZLayer.succeed(Scanamo(LocalDynamoDB.client()))

    val itLayer: ULayer[_root_.zio.test.environment.TestEnvironment with Logging with Has[Scanamo]] =
      (zio.test.environment.testEnvironment ++ logging ++ client).orDie

  }
}
