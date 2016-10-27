package io.github.dispalt.vitess

import java.util.concurrent.TimeUnit

import com.youtube.vitess.proto.grpc.vtgateservice.VitessGrpc
import com.youtube.vitess.proto.grpc.vtgateservice.VitessGrpc.VitessStub
import com.youtube.vitess.proto.query.BoundQuery
import com.youtube.vitess.proto.topodata.TabletType
import com.youtube.vitess.proto.vtgate._
import io.github.dispalt.vitess.Response._
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import io.grpc.{ Channel, ClientInterceptor, ManagedChannel }
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }

class ManagedClient(val channel: ManagedChannel, val keyspace: String) extends Client with ClientLifecycle {}

object ManagedClient {
  def apply(host: String, port: Int, keyspace: String): ManagedClient = {
    new ManagedClient(NettyChannelBuilder
                        .forAddress(host, port)
                        .nameResolverFactory(new DnsNameResolverProvider())
                        .usePlaintext(true)
                        .build,
                      keyspace)
  }
}

class InterceptedClient(val channel: Channel, val keyspace: String, interceptors: ClientInterceptor*) extends Client {
  override def client(implicit ctx: VitessCallerCtx): VitessStub = {
    super.client.withInterceptors(interceptors: _*)
  }
}

/**
  * Handles Client operations, a mix of transaction, execute and streaming.
  *
  */
trait Client {

  val channel: Channel
  val keyspace: String

  val logger = LoggerFactory.getLogger(classOf[Client])

  private val stub = VitessGrpc.stub(channel)

  def client(implicit ctx: VitessCallerCtx): VitessStub = {
    stub.withDeadlineAfter(ctx.timeoutMs, TimeUnit.MILLISECONDS)
  }

  // ~~~~~~~~~~~~~~~~~~~~~
  // Low Level stuff
  // ~~~~~~~~~~~~~~~~~~~~~
  def execute(query: String, bind: Map[String, _], tabletType: TabletType)(implicit ctx: VitessCallerCtx,
                                                                           ec: ExecutionContext): RpcResponse = {
    execute(Proto.bindQuery(query, bind), tabletType)
  }

  /**
    * This is a slightly less low-level call that really does two big things.
    *
    * 1) it converts the result into something nice.
    * 2) It handles threading the TxnEc.  The way [[Session]] works is that every operation
    *    needs a session from the previous response of an operation to continue the txn.  This
    *    uses the TxnEc to achieve that.
    *
    * @param query
    * @param tabletType
    * @param ctx
    * @param ec
    * @return
    */
  def execute(query: BoundQuery, tabletType: TabletType)(implicit ctx: VitessCallerCtx,
                                                         ec: ExecutionContext): RpcResponse = {

    val session = ec match {
      case tec: TransactionalExecutionContext => tec.session
      case _                                  => None
    }

    handleExecutionResponse(
      client.execute(
        ExecuteRequest(query = Some(query),
                       callerId = ctx.callerId,
                       tabletType = tabletType,
                       keyspace = keyspace,
                       session = session)
      )
    )
  }

  /**
    * Executes the [[StreamExecuteRequest]] with vtgate, with some extra help for converting to rows.
    * Below is some information about how fields vs values work.
    *
    *  As returned by StreamExecute, the first QueryResult has the fields
    * set, and subsequent QueryResult have rows set. And as Execute,
    * len(QueryResult[0].fields) is always equal to len(row) (for each
    * row in rows for each QueryResult in QueryResult[1:]).
    *
    * @param query
    * @param bind
    * @param tabletType
    * @param observer
    * @param ctx
    * @param ec
    */
  def streamExecute(query: String, bind: Map[String, _], tabletType: TabletType)(
      observer: StreamObserver[Row])(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Unit = {
    var fieldMap: FieldMap = null

    val se = new StreamObserver[StreamExecuteResponse] {
      def onError(t: Throwable): Unit = {
        observer.onError(t)
      }
      def onCompleted(): Unit = {
        observer.onCompleted()
      }
      def onNext(value: StreamExecuteResponse): Unit = {
        val r = value.getResult
        if (fieldMap == null) {
          fieldMap = FieldMap(value.getResult.fields)
        }
        r.rows.foreach(r => observer.onNext(Row(fieldMap, r)))
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

  /**
    * Begin returns the session ticket, but also attaches it to the [[TransactionalExecutionContext]] if it's available.
    *
    * @param ctx ctx
    * @param ec ec
    * @return
    */
  def begin()(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Future[Option[Session]] = {
    client.begin(BeginRequest(ctx.callerId)).map { c =>
      logger.info("begin succeeded")
      ec match {
        case tec: TransactionalExecutionContext => tec.session = c.session
        case _                                  =>
      }
      c.session
    }
  }

  /**
    * commit finishes out the transaction, and nullifys the session ticket.  It will throw a [[NotInTransaction]] if
    * you do not have a valid [[TransactionalExecutionContext]].
    *
    * @param ctx
    * @param ec
    * @return
    */
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

  /**
    * See [[commit()]] but for rollbacks
    *
    * @param ctx
    * @param ec
    * @return
    */
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

  /**
    * Transaction takes a [[TransactionalExecutionContext]] => [[Future]] and handles all the cleanup of failed attempts
    * etc.
    *
    * @param block
    * @param ctx
    * @param ec
    * @tparam A
    * @return
    */
  def transaction[A](block: TransactionalExecutionContext => Future[A])(implicit ctx: VitessCallerCtx,
                                                                        ec: ExecutionContext): Future[A] = {
    // This technique is for scoping but there is probably a better way.
    val _ec = ec
    def run(): Future[A] = {
      implicit val ec = new TransactionalExecutionContext(_ec, None)
      val p           = Promise[A]()
      begin() onComplete {
        case Success(session) =>
          block(ec).onComplete {
            case Success(res) =>
              p.completeWith(commit().map(_ => res))
            case Failure(fail @ FailedResponse(rpcError, session)) =>
              rollback() onComplete { _ =>
                p.failure(fail)
              }
            case Failure(fail) =>
              logger.error("Unknown failure occurred during execution of transaction", fail)
              p failure fail
          }
        case Failure(fail) =>
          logger.warn("begin call failed")
          p failure fail
      }
      p.future
    }

    run()
  }
}
