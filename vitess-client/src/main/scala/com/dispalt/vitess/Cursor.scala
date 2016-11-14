package com.dispalt.vitess

import com.youtube.vitess.proto.query.QueryResult

class Cursor(qr: QueryResult) extends Iterator[Row] {
  private val it                 = qr.rows.iterator
  private val fieldMap: FieldMap = FieldMap(qr.fields)

  def rowsAffected = qr.rowsAffected

  def hasNext: Boolean = it.hasNext

  def next(): Row = {
    if (hasNext)
      Row(fieldMap, it.next())
    else
      null
  }
}
