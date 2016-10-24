import sbt._
import Keys._

object Version {
  final val GrpcNetty = "1.0.1"
  final val Scala     = "2.11.8"
  final val ScalaTest = "3.0.0"
  final val ScalaPB   = "0.5.43"
  final val Quill     = "0.10.0"
  final val Slf4j     = "1.7.21"
  final val ScalaLog  = "3.5.0"
  final val Netty     = "4.1.6.Final"
}

object Library {
  val scalaTest = "org.scalatest" %% "scalatest" % Version.ScalaTest

  val grpc        = "io.grpc"                % "grpc-netty"            % Version.GrpcNetty
  val scalaPb     = "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % Version.ScalaPB
  val `quill-sql` = "io.getquill"            %% "quill-sql"            % Version.Quill
  val slf4j       = "org.slf4j"              % "slf4j-api"             % Version.Slf4j
  val netty       = "io.netty"               % "netty-codec-http2"     % Version.Netty

  //////////////////
  object Client {
    val dependenciesToShade = Seq(netty)
    val nonShadedDependencies =
      Seq(Library.grpc, Library.slf4j, Library.scalaPb)
  }
}
