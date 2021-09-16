import sbt._

object Dependencies {

  object Versions {
    val awsSdk = "1.11.934"
    val doobie = "0.10.0"

    val circe = "0.13.0"
    val http4s = "0.21.15"

    val pureConfig = "0.14.0"

    val logback = "1.2.3"
    val log4j = "2.14.0"

    val zio = "1.0.3"
    val zioCats = "2.2.0.1"
    val zioLogging = "0.5.4"
    val redis = "3.30"

    val dynamodb = "1.0.0-M11"

  }

  lazy val redis =
    Seq("net.debasishg" %% "redisclient" % Versions.redis)

  lazy val awsSdk = Seq(
    "com.amazonaws" % "aws-java-sdk-cognitoidp" % Versions.awsSdk,
    "com.amazonaws" % "aws-java-sdk-dynamodb"   % Versions.awsSdk,
    "com.amazonaws" % "aws-java-sdk-ses"        % Versions.awsSdk,
    "com.amazonaws" % "aws-java-sdk-lambda"     % Versions.awsSdk
  )

  lazy val javaMail = Seq("com.sun.mail" % "javax.mail" % "1.5.2")
  lazy val dynamodb =
    Seq("com.github.seratch" %% "awscala-dynamodb" % "0.8.+")

  lazy val httpDeps = List(
    "org.http4s" %% "http4s-blaze-server"  % Versions.http4s,
    "org.http4s" %% "http4s-blaze-client"  % Versions.http4s,
    "org.http4s" %% "http4s-circe"         % Versions.http4s,
    "org.http4s" %% "http4s-dsl"           % Versions.http4s,
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    compilerPlugin(("org.typelevel" % "kind-projector" % "0.11.2").cross(CrossVersion.full))
  )

  lazy val circeDeps = List(
    "io.circe"   %% "circe-core"           % Versions.circe,
    "io.circe"   %% "circe-generic"        % Versions.circe,
    "io.circe"   %% "circe-parser"         % Versions.circe,
    "io.circe"   %% "circe-generic-extras" % Versions.circe,
    "io.circe"   %% "circe-literal"        % Versions.circe % "test"
  )

  lazy val pureConfig = Seq("com.github.pureconfig" %% "pureconfig" % Versions.pureConfig)

  lazy val zio = Seq(
    "dev.zio" %% "zio"              % Versions.zio,
    "dev.zio" %% "zio-interop-cats" % Versions.zioCats,
    "dev.zio" %% "zio-test"         % Versions.zio % "test",
    "dev.zio" %% "zio-test-sbt"     % Versions.zio % "test"
  )

  lazy val zioLogging = Seq(
    "dev.zio"       %% "zio-logging"       % Versions.zioLogging,
    "dev.zio"       %% "zio-logging-slf4j" % Versions.zioLogging,
    "ch.qos.logback" % "logback-classic"   % Versions.logback
  )
}
