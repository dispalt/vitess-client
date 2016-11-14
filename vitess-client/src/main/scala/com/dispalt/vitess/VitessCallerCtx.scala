package com.dispalt.vitess

import com.youtube.vitess.proto.vtrpc.CallerID

case class VitessCallerCtx(callerId: Option[CallerID], timeoutMs: Long = 30000)

object VitessCallerCtx {
  val empty = new VitessCallerCtx(None)
}
