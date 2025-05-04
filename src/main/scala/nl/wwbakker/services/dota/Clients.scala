package nl.wwbakker.services.dota

import sttp.capabilities.zio.ZioStreams
import sttp.client4.WebSocketStreamBackend
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.{Task, ZLayer}

object Clients {
  type SttpClient = WebSocketStreamBackend[Task, ZioStreams]

  class HttpClient(inner: SttpClient) extends SttpClient:
    export inner.*

  val live: ZLayer[Any, Throwable, HttpClient] =
    ZLayer.scoped(
      HttpClientZioBackend.scoped().map(HttpClient(_))
    )



}
