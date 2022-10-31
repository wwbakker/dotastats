package nl.wwbakker.services.dota

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.{Layer, Task}

object Clients {
  type SttpClient = SttpBackend[Task, ZioStreams with WebSockets]

  val live: Layer[Throwable, SttpClient] = AsyncHttpClientZioBackend.layer()



}
