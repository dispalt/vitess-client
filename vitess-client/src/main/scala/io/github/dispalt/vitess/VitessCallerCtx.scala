package io.github.dispalt.vitess

import com.youtube.vitess.proto.vtrpc.CallerID

case class VitessCallerCtx(callerId: Option[CallerID])

object VitessCallerCtx {
  val empty = new VitessCallerCtx(None)
}
