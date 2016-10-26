package io.github.dispalt.vitess

import java.util.concurrent.TimeUnit

import com.youtube.vitess.proto.grpc.vtgateservice.VitessGrpc
import com.youtube.vitess.proto.query.BoundQuery
import com.youtube.vitess.proto.topodata.TabletType
import com.youtube.vitess.proto.vtgate._
import com.youtube.vitess.proto.vtrpc.CallerID
import io.github.dispalt.vitess.Response._
import io.grpc.ManagedChannel
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import tabletmanagerdata.tabletmanagerdata.{ ApplySchemaRequest, ApplySchemaResponse }
import tabletmanagerservice.tabletmanagerservice.TabletManagerGrpc
import vtctldata.vtctldata.ExecuteVtctlCommandRequest
import vtctlservice.vtctlservice.VtctlGrpc

import scala.concurrent.{ ExecutionContext, Future }

case class VitessCallerCtx(callerId: Option[CallerID])

object VitessCallerCtx {
  val empty = new VitessCallerCtx(None)
}

class Client(channel: ManagedChannel, keyspace: String) extends BaseClient(channel, keyspace) {

  val logger = LoggerFactory.getLogger(classOf[Client])

  def this(host: String, port: Int, keyspace: String) = {
    this(NettyChannelBuilder
           .forAddress(host, port)
           .nameResolverFactory(new DnsNameResolverProvider())
           .usePlaintext(true)
           .build,
         keyspace)
  }

  val client = VitessGrpc.stub(channel)

  // Context ctx, String query, Map<String, ?> bindVars, TabletType tabletType
  def execute(query: String, bind: Map[String, _], tabletType: TabletType)(implicit ctx: VitessCallerCtx,
                                                                           ec: ExecutionContext): RpcResponse = {

    val session = ec match {
      case tec: TransactionalExecutionContext => tec.session
      case _                                  => None
    }

    handleResponse(
      client.execute(
        ExecuteRequest(query = Some(Proto.bindQuery(query, bind)),
                       callerId = ctx.callerId,
                       tabletType = tabletType,
                       keyspace = keyspace,
                       session = session)
      )
    )
  }

  def execute(query: BoundQuery, tabletType: TabletType)(implicit ctx: VitessCallerCtx,
                                                         ec: ExecutionContext): RpcResponse = {

    val session = ec match {
      case tec: TransactionalExecutionContext => tec.session
      case _                                  => None
    }

    handleResponse(
      client.execute(
        ExecuteRequest(query = Some(query),
                       callerId = ctx.callerId,
                       tabletType = tabletType,
                       keyspace = keyspace,
                       session = session)
      )
    )
  }

  def streamExecute(query: String, bind: Map[String, _], tabletType: TabletType)(
      observer: StreamObserver[Row])(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Unit = {

    import scala.collection.JavaConverters._

    val se = new StreamObserver[StreamExecuteResponse] {
      def onError(t: Throwable): Unit = {
        observer.onError(t)
      }
      def onCompleted(): Unit = {
        observer.onCompleted()
      }
      def onNext(value: StreamExecuteResponse): Unit = {
        new Cursor(value.getResult).foreach(observer.onNext)
      }
    }

    client.streamExecute(
      StreamExecuteRequest(query = Some(Proto.bindQuery(query, bind)),
                           callerId = ctx.callerId,
                           tabletType = tabletType,
                           keyspace = keyspace),
      se
    )
  }

  def begin()(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Future[Option[Session]] = {
    client.begin(BeginRequest(ctx.callerId)).map { c =>
      logger.info("begin succeeded")
      c.session
    }
  }

  def commit()(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Future[Unit] = {
    ec match {
      case tec: TransactionalExecutionContext =>
        client.commit(CommitRequest(ctx.callerId, tec.session)).map { cr =>
          logger.info("committed, clearing session token")
          tec.session = None
        }
      case _ => Future.failed(NotInTransaction)
    }
  }

  def rollback()(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Future[_] = {
    ec match {
      case tec: TransactionalExecutionContext =>
        client.rollback(RollbackRequest(ctx.callerId, tec.session)).map { cr =>
          logger.info("rolled back, clearing session token")
          tec.session = None
        }
      case _ => Future.failed(NotInTransaction)
    }

  }

}
