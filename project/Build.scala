import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.GitPlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import de.heikoseeberger.sbtheader.license._
import sbt._
import sbt.plugins.JvmPlugin
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.{ ReleaseStep, _ }
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.pgp.PgpKeys._

object Build extends AutoPlugin {

  override def requires =
    JvmPlugin && HeaderPlugin

  override def trigger = allRequirements

  override def projectSettings =
    Vector(
      // Core settings
      organization := "com.dispalt",
      crossScalaVersions := Seq(scalaVersion.value), // Add 2.12 when quill upgrades
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("https://github.com/dispalt/vitess-client")),
      description := "Vitess client including quill bindings.",
      mappings.in(Compile, packageBin) += baseDirectory.in(ThisBuild).value / "LICENSE" -> "LICENSE",
      scalaVersion := Version.Scala,
      scalacOptions ++= Vector(
        "-unchecked",
        "-deprecation",
        "-language:_",
        "-target:jvm-1.8",
        "-encoding",
        "UTF-8"
      ),
      resolvers += Resolver.jcenterRepo,
      unmanagedSourceDirectories.in(Compile) := Vector(scalaSource.in(Compile).value),
      unmanagedSourceDirectories.in(Test) := Vector(scalaSource.in(Test).value),
      // Git settings
      git.useGitDescribe := true,
      // Header settings
      headers := Map("scala" -> Apache2_0("2016", "Dan Di Spaltro")),
      // Release process
      publishMavenStyle := true
    ) ++ extras

  lazy val extras: Seq[Setting[_]] = Seq(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) =>
        Seq(compilerPlugin("com.milessabin" % "si2712fix-plugin" % Version.Si2712fix cross CrossVersion.full))
      case _ => Nil
    }),
    scalacOptions := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 11)) => scalacOptions.value
        case _             => scalacOptions.value.filterNot(_.equals("-Yinline-warnings")) ++ Seq("-Ypartial-unification")
      }
    }
  )

  def preventPublication =
    Seq(publishTo := Some(Resolver.file("Unused transient repository", target.value / "fakepublish")),
        publishArtifact := false,
        publish := (),
        publishLocalSigned := (), // doesn't work
        publishSigned := (), // doesn't work
        packagedArtifacts := Map.empty) // doesn't work - https://github.com/sbt/sbt-pgp/issues/42
}
