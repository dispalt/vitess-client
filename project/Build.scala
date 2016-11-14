import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.GitPlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import de.heikoseeberger.sbtheader.license._
import org.scalafmt.sbt.ScalaFmtPlugin
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import sbt._
import sbt.plugins.JvmPlugin
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.{ ReleaseStep, _ }
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.pgp.PgpKeys._

object Build extends AutoPlugin {

  override def requires =
    JvmPlugin && HeaderPlugin && ScalaFmtPlugin

  override def trigger = allRequirements

  override def projectSettings =
    Vector(
      // Core settings
      organization := "com.dispalt",
      crossScalaVersions := Seq(scalaVersion.value, "2.12.0"),
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
      // scalafmt settings
      scalafmtConfig := Some(baseDirectory.in(ThisBuild).value / ".scalafmt.conf"),
      // Git settings
      git.useGitDescribe := true,
      // Header settings
      headers := Map("scala" -> Apache2_0("2016", "Dan Di Spaltro")),
      // Release process
      publishMavenStyle := true,
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        runTest,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        ReleaseStep(action = Command.process("publishSigned", _)),
        setNextVersion,
        commitNextVersion,
        ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
        pushChanges
      )
    )

  def publishSettings =
    Seq(
      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
      },
      pomExtra :=
        <scm>
          <connection>scm:git:https://github.com/dispalt/vitess-client.git</connection>
          <developerConnection>scm:git:git@github.com:dispalt/vitess-client.git</developerConnection>
          <url>http://github.com/dispalt/vitess-client/tree/master</url>
        </scm>
        <developers>
          <developer>
            <id>dispalt</id>
            <name>Dan Di Spaltro</name>
            <organizationUrl>http://dispalt.com</organizationUrl>
          </developer>
        </developers>
    )

  def releaseSettings = publishSettings ++ Seq(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      ReleaseStep(action = Command.process("publishSigned", _)),
      setNextVersion,
      commitNextVersion,
      ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
      pushChanges
    )
  )

  def preventPublication =
    Seq(publishTo := Some(Resolver.file("Unused transient repository", target.value / "fakepublish")),
        publishArtifact := false,
        publish := (),
        publishLocalSigned := (), // doesn't work
        publishSigned := (), // doesn't work
        packagedArtifacts := Map.empty) // doesn't work - https://github.com/sbt/sbt-pgp/issues/42
}
