import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import org.scalafmt.sbt._
import sbt.Keys._
import sbt._

object Settings {
  private final val defaultOrganization = "io.cryptoelevate"
  private final val defaultScalaVersion = "2.13.4"
  private final val defaultScalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Xlint:adapted-args",
    "-Wunused:imports",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:locals",
    "-Wunused:explicits",
    "-Wunused:implicits",
    "-Wunused:linted",
    "-Wdead-code",
    "-Ymacro-annotations"
  )

  implicit final class ProjectFrom(project: Project) {
    def minimalSettings: Project =
      project
        .settings(
          organization := defaultOrganization,
          scalaVersion := defaultScalaVersion,
          scalacOptions ++= defaultScalacOptions
        )

    def commonSettings: Project =
      project.minimalSettings.scalafmtSettings.settings(
        testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
      )

    def scalafmtSettings: Project =
      project.enablePlugins(ScalafmtPlugin)

    def dockerSettings: Project =
      /* val runAsUser = "daemon"

      def runAsRoot(cmdLike: CmdLike*): Seq[CmdLike] =
        (Cmd("USER", "root") +: cmdLike) :+ Cmd("USER", runAsUser)*/

      project
        .enablePlugins(JavaServerAppPackaging)
        .enablePlugins(DockerPlugin)
    /* .settings(
          dockerBaseImage := "adoptopenjdk/openjdk8:alpine-slim",
          dockerUpdateLatest := true,
          dockerCommands ++= runAsRoot(Cmd("RUN", "/sbin/apk", "add", "--no-cache", "bash"))
        )*/
  }

}
