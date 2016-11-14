package io.github.dispalt.vitess

import com.trueaccord.scalapb.TextFormat
import com.youtube.vitess.proto.vtgate.Session

import scala.concurrent.ExecutionContext

class TransactionalExecutionContext(val ec: ExecutionContext, var session: Option[Session]) extends ExecutionContext {
  override def toString: String = {
    s"TransactionalExecutionContext($ec, ${session.map(TextFormat.printToSingleLineUnicodeString)})"
  }

  def execute(runnable: Runnable): Unit     = ec.execute(runnable)
  def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
}
