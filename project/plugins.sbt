// Plugins

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.6")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0-pre2"

addSbtPlugin("com.typesafe.sbt"  % "sbt-git"      % "0.8.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header"   % "1.6.0")
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.3")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "1.1")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.0.0")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly" % "0.14.3")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.6.3")

// End
