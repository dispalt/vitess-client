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
      organization := "io.github.dispalt",
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("http://github.com/dispalt/vitess")),
      description := "sri relay package.",
      mappings.in(Compile, packageBin) += baseDirectory.in(ThisBuild).value / "LICENSE" -> "LICENSE",
      scalaVersion := Version.Scala,
      crossScalaVersions := Vector(scalaVersion.value),
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
          <connection>scm:git:git://github.com/dispalt/sri-vdom.git</connection>
          <developerConnection>scm:git:ssh://github.com:dispalt/sri-vdom.git</developerConnection>
          <url>http://github.com/dispalt/sri-vdom/tree/master</url>
        </scm>
        <developers>
          <developer>
            <id>dispalt</id>
            <name>Dan Di Spaltro</name>
            <organizationUrl>http://github.com/dispalt</organizationUrl>
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

  def replaceDep(document: xml.Node, replacedNode: xml.Node): xml.Node = {

    val gid = (replacedNode \ "groupId").head.text
    val aid = (replacedNode \ "artifactId").head.text

    def find(deps: Seq[xml.Node], parent: xml.Node): Seq[xml.Node] = deps map {
      case elem: xml.Elem if elem.label == "dependency" =>
        val thisGid = (elem \ "groupId").head.text
        val thisAid = (elem \ "artifactId").head.text
        if (thisGid == gid && thisAid == aid) {
          replacedNode
        } else {
          elem
        }
      case node => node
    }

    document match {
      case elem: xml.Elem =>
        val child = if (elem.label == "dependencies") {
          find(elem.child, elem)
        } else {
          elem.child.map(replaceDep(_, replacedNode))
        }
        xml.Elem(elem.prefix, elem.label, elem.attributes, elem.scope, false, child: _*)
      case _ =>
        document
    }
  }

  def shadedMinusNetty(groupId: String, artifactId: String, version: String): scala.xml.Node = {
    <dependency>
      <groupId>{groupId}</groupId>
      <artifactId>{artifactId}</artifactId>
      <version>{version}</version>
      <exclusions>
        <exclusion>
          <groupId>io.netty</groupId>
          <artifactId>netty-codec-http2</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
  }

  def preventPublication =
    Seq(publishTo := Some(Resolver.file("Unused transient repository", target.value / "fakepublish")),
        publishArtifact := false,
        publish := (),
        publishLocalSigned := (), // doesn't work
        publishSigned := (), // doesn't work
        packagedArtifacts := Map.empty) // doesn't work - https://github.com/sbt/sbt-pgp/issues/42
}
