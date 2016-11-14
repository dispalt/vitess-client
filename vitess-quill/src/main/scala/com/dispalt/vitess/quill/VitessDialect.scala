package com.dispalt.vitess.quill

import io.getquill.MySQLDialect

trait VitessDialect extends MySQLDialect {
  override def liftingPlaceholder(index: Int): String = {
    s":v$index"
  }
}

object VitessDialect extends VitessDialect
