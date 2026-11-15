package dawidkobak.myfunds.implicits

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import zio.{Tag, Task}

object SttpClientImplicits {
  private type ZIOStreamsWebsockets = ZioStreams & WebSockets
  implicit val sttpClientTag: Tag[SttpBackend[Task, ZioStreams & WebSockets]] = Tag.materialize[SttpBackend[Task, ZIOStreamsWebsockets]]
}
