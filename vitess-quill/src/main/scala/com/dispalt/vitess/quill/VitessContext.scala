package com.dispalt.vitess.quill

import java.util.Date

import io.github.dispalt.vitess.Response.RpcResponse
import io.github.dispalt.vitess._
import com.google.common.primitives.UnsignedLong
import com.typesafe.scalalogging.Logger
import com.youtube.vitess.proto.query.{ BindVariable, BoundQuery }
import com.youtube.vitess.proto.topodata.TabletType
import io.getquill.{ MySQLDialect, NamingStrategy }
import io.getquill.context.sql.SqlContext
import io.getquill.idiom.{ Idiom => BaseIdiom }
import io.getquill.idiom.Idiom
import io.github.dispalt.vitess.Client
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.{ DynamicVariable, Failure, Success, Try }

class VitessContext[Naming <: NamingStrategy](client: ManagedClient, _ctx: VitessCallerCtx, tabletType: TabletType)
    extends SqlContext[VitessDialect, Naming]
    with VitessEncoder
    with VitessDecoder {

  protected val logger: Logger =
    Logger(LoggerFactory.getLogger(classOf[VitessContext[_]]))

  type PrepareRow = BoundQuery
  type ResultRow  = Row

  type RunQueryResult[T]                = Future[List[T]]
  type RunQuerySingleResult[T]          = Future[T]
  type RunActionResult                  = Future[Long]
  type RunActionReturningResult[T]      = Future[T]
  type RunBatchActionResult             = Future[List[Long]]
  type RunBatchActionReturningResult[T] = Future[List[T]]

  def probe(statement: String): Try[_] = Try {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val ctx = VitessCallerCtx.empty
    Await.result(withClient(_.execute(statement, Map.empty, TabletType.MASTER)), Duration.Inf)
  }

  def close(): Unit = client.closeBlocking()

  // Not used yet.
  // TODO: I don't think I need to the TxnEc stuff anymore here.
  def handleEx[T](r: RpcResponse)(f: Response => T)(implicit ctx: VitessCallerCtx, ec: ExecutionContext): Future[T] = {
    val p = Promise[T]()
    ec match {
      case tec: TransactionalExecutionContext =>
        r.onComplete {
          case Success(Left(failure)) =>
            tec.session = failure.session
            p.failure(failure)
          case Success(Right(ok)) =>
            tec.session = ok.session
            p.success(f(ok))
          case Failure(fail) =>
            // Unknown error
            p.failure(fail)
        }
      case _ =>
        r.onComplete {
          case Success(Left(failure)) => p.failure(failure)
          case Success(Right(ok))     => p.success(f(ok))
          case Failure(fail)          => p.failure(fail) // Unknown error
        }

    }

    p.future
  }

  def executeQuery[T](
      sql: String,
      prepare: PrepareRow => PrepareRow = identity,
      extractor: Row => T = identity[Row] _
  )(implicit ec: ExecutionContext, ctx: VitessCallerCtx): Future[List[T]] = {
    logger.info(sql)
    val bq = prepare(BoundQuery(sql = sql))

    withClient(
      cli =>
        handleEx(cli.execute(bq, tabletType)) { f =>
          f.value.map(extractor).toList
      }
    )

  }

  def executeAction[T](
      sql: String,
      prepare: PrepareRow => PrepareRow = identity
  )(implicit ec: ExecutionContext, ctx: VitessCallerCtx): Future[Long] = {
    logger.info(sql)
    val bq = prepare(BoundQuery(sql = sql))

    withClient(
      cli =>
        handleEx(cli.execute(bq, tabletType)) { f =>
          f.value.rowsAffected
      }
    )
  }

  def transaction[T](f: TransactionalExecutionContext => Future[T])(implicit ctx: VitessCallerCtx,
                                                                    ec: ExecutionContext): Future[T] = {
    client.transaction(f)
  }

  def executeBatchAction(groups: List[BatchGroup])(implicit ec: ExecutionContext,
                                                   ctx: VitessCallerCtx): Future[List[Long]] =
    Future.sequence {
      groups.map {
        case BatchGroup(sql, prepare) =>
          prepare.foldLeft(Future.successful(List.empty[Long])) {
            case (acc, prepare) =>
              acc.flatMap { list =>
                executeAction(sql, prepare).map(list :+ _)
              }
          }
      }
    }.map(_.flatten.toList)

  private def withClient[T](f: Client => T) =
    f(client)

}

trait VitessEncoder { this: VitessContext[_] =>

  def encoder[T: ClassTag]: Encoder[T] =
    new Encoder[T] {
      def apply(index: Int, value: T, row: BoundQuery) = {
        row.addBindVariables(s"v$index" -> Proto.buildBindVariable(value))
      }
    }

  implicit def optionEncoder[T](implicit d: Encoder[T]): Encoder[Option[T]] = ???

  implicit val stringEncoder: Encoder[String]         = encoder
  implicit val bigDecimalEncoder: Encoder[BigDecimal] = encoder
  implicit val booleanEncoder: Encoder[Boolean]       = encoder
  implicit val byteEncoder: Encoder[Byte]             = encoder
  implicit val shortEncoder: Encoder[Short]           = encoder
  implicit val intEncoder: Encoder[Int]               = encoder
  implicit val longEncoder: Encoder[Long]             = encoder
  implicit val floatEncoder: Encoder[Float]           = encoder
  implicit val doubleEncoder: Encoder[Double]         = encoder
  implicit val byteArrayEncoder: Encoder[Array[Byte]] = encoder
  implicit val dateEncoder: Encoder[Date]             = encoder
}

trait VitessDecoder { this: VitessContext[_] =>

  import io.getquill.util.Messages._
  def decoder[T] = new Decoder[T] {
    def apply(index: Int, row: ResultRow): T = {
      row.get[T](index)
    }
  }

  def typedDecoder[T: ClassTag](f: PartialFunction[Any, T]): Decoder[T] =
    new Decoder[T] {
      def apply(index: Int, row: ResultRow) = {
        val value = row.getAny(index)
        f.lift(value).getOrElse(fail(s"Value '$value' can't be decoded to '${classTag[T].runtimeClass}'"))
      }
    }

  implicit def optionDecoder[T](implicit d: Decoder[T]): Decoder[Option[T]] = new Decoder[Option[T]] {
    def apply(index: Int, row: ResultRow): Option[T] = {
      Option(d(index, row))
    }
  }

  implicit val stringDecoder: Decoder[String] = typedDecoder[String] {
    case a: Array[Byte] => new String(a)
  }

  implicit val bigDecimalDecoder: Decoder[BigDecimal] = decoder[BigDecimal]
  implicit val booleanDecoder: Decoder[Boolean] = typedDecoder[Boolean] {
    case i: Integer                => i == 1
    case i: Long                   => i == 1
    case i: UnsignedLong           => i.compareTo(UnsignedLong.ONE) == 0
    case i: Array[Byte @unchecked] => i(0) == 1
  }

  implicit val byteDecoder: Decoder[Byte]             = decoder
  implicit val shortDecoder: Decoder[Short]           = decoder
  implicit val intDecoder: Decoder[Int]               = decoder
  implicit val longDecoder: Decoder[Long]             = decoder
  implicit val floatDecoder: Decoder[Float]           = decoder
  implicit val doubleDecoder: Decoder[Double]         = decoder
  implicit val byteArrayDecoder: Decoder[Array[Byte]] = decoder
  implicit val dateDecoder: Decoder[Date]             = decoder

}
