# Vitess Client for Scala

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.dispalt/vitess-client_2.11/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/com.dispalt/vitess-client_2.11)


This is an implementation of the [Vitess](http://vitess.io) grpc interface using a full scala toolchain.
The reason for this is three-fold.  

* Using [ScalaPB](https://github.com/trueaccord/ScalaPB) gives a more scala-like interface.
* Netty 4.1 incompatibility. There are two clients one is shaded the other is not. Netty 4.0 to 4.1 introduced
multiple binary incompatibilities which makes it hard to integrate two different dependency trees
* I can build a [Quill](https://github.com/getquill/quill/) adapter for [Vitess](http://vitess.io), 
which is a great lightweight compile-time SQL abstraction.

## Quick start

### SBT

In your `build.sbt` include the build coordinates hosted on maven like so.

```scala
libraryDependencies ++= Seq(
    "com.dispalt" %% "vitess-client" % "0.2.0"
)
```

Or to use the shaded version instead, include the following artifact.

```scala
libraryDependencies ++= Seq(
    "com.dispalt" %% "vitess-shade" % "0.2.0"
)
```

# License

Apache 2.0, see the LICENSE file for a full copy.
