import Dependencies._
import Settings._
import com.typesafe.sbt.packager.Keys.packageName

lazy val core = (project in file("modules/core")).commonSettings
  .settings(name := "ce-core", libraryDependencies ++= pureConfig ++ zio ++ zioLogging)

lazy val connectors = (project in file("modules/connectors")).commonSettings
  .settings(name := "ce-connectors", libraryDependencies ++= awsSdk ++ zio ++ dynamodb ++ javaMail ++ redis ++ circeDeps)
  .dependsOn(core % "compile->compile; test->test")

lazy val dataService = (project in file("modules/data-service")).commonSettings
  .settings(name := "ce-data-service", libraryDependencies ++= zio)
  .dependsOn(connectors % "compile->compile; test->test")

lazy val http = (project in file("modules/http")).commonSettings.dockerSettings
  .settings(
    name := "ce-http",
    packageName in Docker := "crypto-elevate-api",
    dockerExposedPorts := Seq(8080),
    mainClass in (Compile, run) := Some("io.cryptoelevate.http.Main"),
    libraryDependencies ++= httpDeps ++ circeDeps ++ zio
  )
  .dependsOn(dataService % "compile->compile; test->test")

lazy val root = (project in file(".")).commonSettings
  .settings(name := "ce-root")
  .aggregate(core, connectors, dataService, http)

addCommandAlias("fmt", "; scalafmt; scalafmtSbt; test:scalafmt; it:scalafmt")
addCommandAlias("checkFmt", "; scalafmtSbtCheck; scalafmtCheck; test:scalafmtCheck; it:scalafmtCheck")
addCommandAlias("checkCvrg", "; coverage ; test ; it:test; coverageAggregate")
addCommandAlias("compileAll", "; clean ; compile ; test:compile; it:compile")
addCommandAlias("checkUnits", "; checkFmt ; compileAll ; test")
addCommandAlias("checkAll", "; checkUnits ; it:test")
