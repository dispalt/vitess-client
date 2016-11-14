package com.dispalt.vitess

import java.util.concurrent.TimeUnit

import io.grpc.{ Channel, ManagedChannel }
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.netty.NettyChannelBuilder

import scala.concurrent.{ ExecutionContext, Future }

trait ClientLifecycle {

  val channel: ManagedChannel

  def closeBlocking(): Unit = {
    channel.shutdown()
    channel.awaitTermination(30, TimeUnit.SECONDS)
  }

  def close()(implicit ec: ExecutionContext): Future[Unit] = Future {
    channel.shutdown()
    channel.awaitTermination(30, TimeUnit.SECONDS)
  }
}
