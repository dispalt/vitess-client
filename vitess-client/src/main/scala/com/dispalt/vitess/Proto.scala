package io.github.dispalt.vitess

import com.google.common.primitives.UnsignedLong
import com.google.protobuf.ByteString
import com.youtube.vitess.proto.query.{ BindVariable, BoundQuery, Type, Value }

object Proto {

  /**
    * bindQuery creates a BoundQuery from query and vars.
    */
  def bindQuery(query: String, vars: Map[String, _]): BoundQuery = {
    val m = vars.map { p =>
      (p._1, buildBindVariable(p._2))
    }
    BoundQuery(query, m)
  }

  def buildBindVariable(value: Any): BindVariable = {
    value match {
      case b: BindVariable => b
      case b: TraversableOnce[_] if b.nonEmpty =>
        val bv = BindVariable(Type.TUPLE)
        val v = b map { f =>
          typedValue(f)
        }
        bv.addAllValues(v)
      case f =>
        val tv = typedValueTuple(f)
        BindVariable(tv._1, tv._2)
    }
  }

  def typedValueTuple(obj: Any): (Type, ByteString) = {
    val p = obj match {
      case null            => (Type.NULL_TYPE, ByteString.EMPTY)
      case s: String       => (Type.VARCHAR, ByteString.copyFromUtf8(s))
      case v: Array[Byte]  => (Type.VARBINARY, ByteString.copyFrom(v))
      case n: UnsignedLong => (Type.UINT64, ByteString.copyFromUtf8(n.toString))
      case n: Float        => (Type.FLOAT64, ByteString.copyFromUtf8(n.toString))
      case n: Double       => (Type.FLOAT64, ByteString.copyFromUtf8(n.toString))
      case b: Boolean      => (Type.INT64, ByteString.copyFromUtf8(if (b) "1" else "0"))
      case b: BigDecimal =>
        val res = if (b.scale > MAX_DECIMAL_UNIT) {
          // MySQL only supports scale up to 30.
          b.setScale(MAX_DECIMAL_UNIT, BigDecimal.RoundingMode.HALF_UP)
        } else b

        Type.DECIMAL -> ByteString.copyFromUtf8(res.toString)
      case n: Number => (Type.INT64, ByteString.copyFromUtf8(n.toString))
      case _         => throw new IllegalArgumentException("unsupported type for Value proto: " + obj.getClass)

    }
    p
  }

  def typedValue(obj: Any): Value = {
    val v = typedValueTuple(obj)
    Value(v._1, v._2)
  }

  private final val MAX_DECIMAL_UNIT = 30
}
