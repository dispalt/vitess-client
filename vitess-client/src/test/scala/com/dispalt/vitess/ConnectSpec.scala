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

import com.youtube.vitess.proto.topodata.TabletType
import org.scalatest._

class ConnectSpec extends AsyncFlatSpec with Matchers {

  behavior of "Vitess client"

  implicit val ctx = VitessCallerCtx.empty

  it should "Connect" in {
    val cli = ManagedClient("127.0.0.1", 15991, "test_keyspace")
    cli.execute("select * from users", Map.empty, TabletType.MASTER).map {f =>
      println(f.right.get.value)
      assert(f.isRight)
    }
  }

}
