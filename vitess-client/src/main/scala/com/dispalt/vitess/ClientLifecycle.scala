/*
 * Copyright 2016 Dan Di Spaltro
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
