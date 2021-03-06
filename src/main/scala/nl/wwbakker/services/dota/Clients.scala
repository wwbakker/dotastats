package nl.wwbakker.services.dota

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import zio.{Has, Task}

object Clients {
  type SttpClient = Has[SttpBackend[Task, ZioStreams with WebSockets]]
}
