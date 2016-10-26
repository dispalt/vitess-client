package io.github.dispalt.vitess

import java.util.concurrent.TimeUnit

import io.grpc.ManagedChannel
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.netty.NettyChannelBuilder

import scala.concurrent.{ ExecutionContext, Future }

class BaseClient(channel: ManagedChannel, keyspace: String) {

  def this(host: String, port: Int, keyspace: String) = {
    this(NettyChannelBuilder
           .forAddress(host, port)
           .nameResolverFactory(new DnsNameResolverProvider())
           .usePlaintext(true)
           .build,
         keyspace)
  }

  def closeBlocking(): Unit = {
    channel.shutdown()
    channel.awaitTermination(30, TimeUnit.SECONDS)
  }

  def close()(implicit ec: ExecutionContext): Future[Unit] = Future {
    channel.shutdown()
    channel.awaitTermination(30, TimeUnit.SECONDS)
  }
}
