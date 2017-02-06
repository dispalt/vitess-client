import ReleaseTransformations._

scalaVersion := Version.Scala

lazy val `vitess` =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitVersioning)
    .settings(Build.preventPublication)
    .aggregate(`vitess-shade`, `vitess-quill`, `vitess-client`)

lazy val `vitess-client` =
  project
    .in(file("vitess-client"))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(
      PB.targets in Compile := Seq(
        scalapb.gen(singleLineToString = true) -> (sourceManaged in Compile).value
      ),
      libraryDependencies ++= Library.Client.dependenciesToShade ++ Library.Client.nonShadedDependencies,
      releaseSettings
    )

lazy val `vitess-shade` =
  project
    .in(file("vitess-shade"))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(
      // Just get whatever asset is built in vitess-client
      exportedProducts in Compile := (exportedProducts in Compile in `vitess-client`).value,
      // This is the total classpath stolen from the non shaded version
      fullClasspath in assembly := {
        val f = (externalDependencyClasspath in Compile in `vitess-client`).value
        val e = (exportedProducts in Compile in `vitess-client`).value
        f ++ e
      },
//      fullClasspath in assembly := (fullClasspath in Compile).value,
      // Protobuf is already included so we only add slf4j
      libraryDependencies ++= Seq(Library.slf4j),
      assemblyOption in assembly := (assemblyOption in assembly).value
        .copy(includeScala = false, includeDependency = true),
      // We only really need to rename netty because of shitty 4.0 vs 4.1 issues.
      assemblyShadeRules in assembly := Seq(
        ShadeRule.rename("io.netty.**" -> "shadenetty.@1").inAll
      ),
      assemblyMergeStrategy in assembly := {
        case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      publishArtifact in (Compile, packageBin) := false,
      assemblyExcludedJars in assembly := {
        val cp = (fullClasspath in assembly).value
        cp filter {
          // We keep google protobuf and slf4j since they work with most stuff
          case f if f.data.getName.startsWith("protobuf-java") => true
          case f if f.data.getName.startsWith("slf4j")         => true
          // Include the rest
          case f => false
        }
      },
      addArtifact(artifact in Compile, assembly),
      releaseSettings
    )

lazy val `vitess-quill` =
  project
    .in(file("vitess-quill"))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(
      libraryDependencies ++= Seq(
        Library.`quill-sql`
      ),
      unmanagedJars in Compile := Seq((assembly in (`vitess-shade`, assembly)).value).classpath,
      releaseSettings
    )


// Borrowed from the awesome people at https://github.com/getquill/quill/blob/master/build.sbt
def updateReadmeVersion(selectVersion: sbtrelease.Versions => String) =
  ReleaseStep(action = st => {

    val newVersion = selectVersion(st.get(ReleaseKeys.versions).get)

    import scala.io.Source
    import java.io.PrintWriter

    val pattern = """"com.dispalt" %% "vitess-.*" % "(.*)"""".r

    val fileName = "README.md"
    val content  = Source.fromFile(fileName).getLines.mkString("\n")

    val newContent =
      pattern.replaceAllIn(content, m => m.matched.replaceAllLiterally(m.subgroups.head, newVersion))

    new PrintWriter(fileName) { write(newContent); close }

    val vcs = Project.extract(st).get(releaseVcs).get
    vcs.add(fileName).!

    st
  })

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
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    updateReadmeVersion(_._1),
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(action = Command.process("publishSigned", _)),
    setNextVersion,
    updateReadmeVersion(_._2),
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges
  )
)
