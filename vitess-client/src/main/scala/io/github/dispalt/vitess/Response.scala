package io.github.dispalt.vitess

import com.google.protobuf.ByteString
import com.youtube.vitess.proto.query.{ Field, QueryResult, Row => RRow }
import com.youtube.vitess.proto.vtgate.{ ExecuteResponse, Session }
import com.youtube.vitess.proto.vtrpc.RPCError

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, postfixOps }
import scala.util.{ Failure, Success }

case class FieldMap(fields: Vector[Field]) {
  private val _fieldIdx: Map[String, Int] = fields.zipWithIndex.map {
    case (f, idx) => f.name.toLowerCase() -> idx
  }(collection.breakOut)

  def get(name: String): Option[Field] = {
    _fieldIdx.get(name.toLowerCase).map(i => fields(i))
  }

  def get(idx: Int): Field = {
    fields(idx)
  }

  def getT(name: String): Option[(Field, Int)] = {
    _fieldIdx.get(name.toLowerCase).map(i => (fields(i), i))
  }

  def getIndex(name: String): Option[Int] = {
    _fieldIdx.get(name)
  }
}

class Row(fields: FieldMap, values: Vector[ByteString]) {
  def getObj(name: String): Option[Any] = {
    fields.getT(name).map {
      case (f, idx) => Row.convertFieldValue(f, values(idx))
    }
  }

  def get[T](idx: Int): T = {
    val f = fields.get(idx)
    Row.convertFieldValue(f, values(idx)).asInstanceOf[T]
  }

  def getAny(idx: Int): Any = {
    val f = fields.get(idx)
    Row.convertFieldValue(f, values(idx))
  }
}

object Row {
  def apply(fields: FieldMap, rawRow: RRow): Row = {
    val bs    = rawRow.values
    var start = 0
    val values = rawRow.lengths map {
      case l if l < 0 => null
      case l =>
        val s = bs.substring(start, start + l.toInt)
        start += l.toInt
        s
    } toVector

    new Row(fields, values = values)
  }

  private def parseDate(value: ByteString): Any = {
    // We don't get time zone information from the server,
    // so we use the default time zone.
    try java.time.LocalDateTime.parse(value.toStringUtf8)
    catch {
      case e: Throwable => throw new Exception("Can't parse DATE: " + value.toStringUtf8, e)
    }

  }

  private def convertFieldValue(field: Field, value: ByteString): Any = {
    import com.youtube.vitess.proto.query.Type._
    // Note: We don't actually know the charset in which the value is encoded.
    // For dates and numeric values, we just assume UTF-8 because they (hopefully) don't contain
    // anything outside 7-bit ASCII, which (hopefully) is a subset of the actual charset.
    // For strings, we return byte[] and the application is responsible for using the right charset.
    field.`type` match {
      case DECIMAL                                                 => BigDecimal(value.toStringUtf8)
      case INT8 | UINT8 | INT16 | UINT16 | INT24 | UINT24 | INT32  => Integer.valueOf(value.toStringUtf8)
      case UINT32 | INT64                                          => java.lang.Long.valueOf(value.toStringUtf8)
      case UINT64                                                  => java.lang.Long.valueOf(value.toStringUtf8)
      case FLOAT32                                                 => java.lang.Float.valueOf(value.toStringUtf8)
      case FLOAT64                                                 => java.lang.Double.valueOf(value.toStringUtf8)
      case NULL_TYPE                                               => null
      case DATE                                                    => parseDate(value)
      case TIME                                                    => parseDate(value)
      case DATETIME | TIMESTAMP                                    => parseDate(value)
      case YEAR                                                    => java.lang.Short.valueOf(value.toStringUtf8)
      case ENUM | SET                                              => value.toStringUtf8
      case BIT | TEXT | BLOB | VARCHAR | VARBINARY | CHAR | BINARY => value.toByteArray
      case _ =>
        throw new Exception("unknown field type: " + field.`type`)
    }
  }
}

class Cursor(qr: QueryResult) extends Iterator[Row] {
  private val it                 = qr.rows.iterator
  private val fieldMap: FieldMap = FieldMap(qr.fields.toVector)

  def rowsAffected = qr.rowsAffected

  def hasNext: Boolean = it.hasNext

  def next(): Row = {
    if (hasNext)
      Row(fieldMap, it.next())
    else
      null
  }
}

case class Response(value: Cursor, session: Option[Session])
case class FailedResponse(rpcError: RPCError, session: Option[Session])
    extends Throwable(
      s"$rpcError, ${session.map(s => com.trueaccord.scalapb.TextFormat.printToSingleLineUnicodeString(s))}"
    )

case object NotInTransaction extends Throwable("Not in an actual transaction, aborting.")

object Response {

  type RpcResponse = Future[FailedResponse Either Response]

  def handleResponse(er: Future[ExecuteResponse])(implicit ec: ExecutionContext): RpcResponse = {
    er.map {
      case ExecuteResponse(Some(err), session, _)   => Left(FailedResponse(err, session))
      case ExecuteResponse(None, session, Some(qr)) => Right(Response(new Cursor(qr), session))
    }
  }

}
