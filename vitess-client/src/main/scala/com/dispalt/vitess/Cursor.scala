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
