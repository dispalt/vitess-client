# Vitess Client for Scala

This is an implementation of the [vitess](http://vitess.io) grpc interface using a full scala toolchain.
The reason for this is three-fold.  

* Using scalapb gives a more scala-like interface.
* There are two clients one is shaded the other is not.  Between 4.0 netty and 4.1 netty there are multiple binary
incompatibilities which makes it hard to integrate two different dependency trees
* I can build a quill adaptor for vitess, which is a great abstraction using the power of macros.

# License

Apache 2.0
