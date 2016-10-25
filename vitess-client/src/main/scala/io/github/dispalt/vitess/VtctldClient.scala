package io.github.dispalt.vitess

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

class VtctldClient(channel: ManagedChannel, keyspace: String) extends BaseClient(channel, keyspace) {

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
