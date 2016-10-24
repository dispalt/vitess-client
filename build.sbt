import ReleaseTransformations._

scalaVersion := Version.Scala

lazy val `vitess` =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitVersioning)
    .settings(publish := {}) aggregate (`vitess-shade`, `vitess-quill`)

lazy val `vitess-client` =
  project
    .in(file("vitess-client"))
    .settings(PB.targets in Compile := Seq(
                scalapb.gen() -> (sourceManaged in Compile).value
              ),
              libraryDependencies ++= Library.Client.dependenciesToShade ++ Library.Client.nonShadedDependencies)

lazy val `vitess-shade` =
  project
    .in(file("vitess-shade"))
    .settings(
      // Just get whatever asset is built in vitess-client
      exportedProducts in Compile := (exportedProducts in Compile in `vitess-client`).value,
      libraryDependencies ++= Seq(Library.slf4j, Library.scalaPb, Library.grpc),
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false,
                                                                            includeDependency = true),
      assemblyShadeRules in assembly := Seq(ShadeRule.rename("io.netty.**" -> "shadenetty.@1").inAll),
      assemblyMergeStrategy in assembly := {
        case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      publishArtifact in (Compile, packageBin) := false,
      pomPostProcess := { (node: xml.Node) =>
        Build.replaceDep(node,
                         Build.shadedMinusNetty(Library.grpc.organization, Library.grpc.name, Library.grpc.revision))
      },
      assemblyExcludedJars in assembly := {
        val cp = (fullClasspath in assembly).value
        cp filter {
          // We have to do it this way because of how protocompiler stuff works.
          case f if f.data.getName.startsWith("protobuf-java") => true
          case f if f.data.getName.startsWith("netty")         => false
          // Skip the rest
          case _ => true
        }
      },
      addArtifact(artifact in Compile, assembly),
      Build.releaseSettings
    )

lazy val `vitess-quill` =
  project
    .in(file("vitess-quill"))
    .dependsOn(`vitess-shade`)
    .settings(
      libraryDependencies ++= Seq(
        Library.`quill-sql`
      ),
      Build.releaseSettings
    )
