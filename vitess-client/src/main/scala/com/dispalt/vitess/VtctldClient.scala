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

import com.youtube.vitess.proto.grpc.vtgateservice.VitessGrpc
import io.grpc.{ ManagedChannel, StatusRuntimeException }
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import vtctldata.vtctldata.{ ExecuteVtctlCommandRequest, ExecuteVtctlCommandResponse }
import vtctlservice.vtctlservice.VtctlGrpc

import scala.concurrent.{ ExecutionContext, Future, Promise }

class VtctldClient(val channel: ManagedChannel, keyspace: String) extends ClientLifecycle {

  def this(host: String, port: Int, keyspace: String) = {
    this(NettyChannelBuilder
           .forAddress(host, port)
           .nameResolverFactory(new DnsNameResolverProvider())
           .usePlaintext(true)
           .build,
         keyspace)
  }

  private val logger = LoggerFactory.getLogger(classOf[VtctldClient])
  val client         = VtctlGrpc.stub(channel)

  // ~~~~~~~~~~~~~~~~~
  // Vtctld
  // ~~~~~~~~~~~~~~~~~~
  def applySchema(sqls: List[String], keyspace: String): Future[Vector[ExecuteVtctlCommandResponse]] = {
    val p   = Promise[Vector[ExecuteVtctlCommandResponse]]()
    var acc = Vector.empty[ExecuteVtctlCommandResponse]

    val so = new StreamObserver[ExecuteVtctlCommandResponse] {
      def onError(t: Throwable): Unit = t match {
        // Probably need to come up with a better solution.
        case grpc: StatusRuntimeException
            if grpc.getStatus.getDescription.indexOf("does not introduce any table definition change.") > 0 =>
          p.success(acc)
        case _ =>
          p.failure(t)
      }
      def onCompleted(): Unit                              = p.success(acc)
      def onNext(value: ExecuteVtctlCommandResponse): Unit = acc = acc :+ value
    }

    client.executeVtctlCommand(
      ExecuteVtctlCommandRequest("ApplySchema" :: "-sql=" + sqls.mkString(";") :: keyspace :: Nil),
      so)
    p.future
  }
}
