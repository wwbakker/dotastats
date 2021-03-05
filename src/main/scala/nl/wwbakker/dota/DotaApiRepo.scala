package nl.wwbakker.dota

import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import sttp.client3.asynchttpclient.zio.{SttpClient, send}
import sttp.client3.{Response, UriContext, basicRequest}
import sttp.model.Uri
import zio.{Has, IO, ZIO, ZLayer}

object DotaApiRepo {

  // Responses
  case class WinLoss(win: Int, lose: Int) {
    def rate: Double = (win.toDouble / (win.toDouble + lose.toDouble)) * 100d
  }

  case class Peer(account_id: Int, last_played: Int,
                  win: Int, games: Int,
                  with_win: Int, with_games: Int,
                  against_win: Int, against_games: Int,
                  with_gpm_sum: Int, with_xpm_sum: Int,
                  personaname: Option[String], name: Option[String],
                  is_contributor: Boolean, last_login: Option[String],
                  avatar: Option[String], avatarfull: Option[String])


  // Service description
  type DotaApiRepo = Has[DotaApiRepo.Service]
  type ErrorMessage = String

  trait Service {
    def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[SttpClient, Throwable, WinLoss]

    def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[SttpClient, Throwable, Seq[Peer]]
  }

  // implementation
  val live: ZLayer[SttpClient, Throwable, DotaApiRepo] = ZLayer.succeed(
    new Service {
      override def winLoss(playerId: Int, numberOfDaysInThePast: Int): ZIO[SttpClient, Throwable, WinLoss] =
        callService[WinLoss](uri"https://api.opendota.com/api/players/$playerId/wl?significant=0&date=$numberOfDaysInThePast")

      override def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[SttpClient, Throwable, Seq[Peer]] =
        callService[Seq[Peer]](uri"https://api.opendota.com/api/players/$playerId/peers?significant=0&date=$numberOfDaysInThePast")
    }
  )

  private def callService[A: Decoder](uri: Uri): ZIO[SttpClient, Throwable, A] =
    for {
      response <- send(basicRequest.get(uri))
      response200 <- filter200Response(response)
      decoded <- decodeTo[A](response200)
    } yield decoded

  private def filter200Response(response: Response[Either[String, String]]): IO[Throwable, String] =
    ZIO.fromEither(response.body.swap.map(e =>
      new IllegalStateException(s"Received error from service:\n $e")).swap)

  private def decodeTo[A: Decoder](body: String): IO[Throwable, A] =
    IO.fromEither(decode[A](body).swap.map(new IllegalStateException(_)).swap)


  // API
  def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[DotaApiRepo with SttpClient, Throwable, WinLoss] =
    ZIO.accessM(_.get.winLoss(playerId, numberOfDaysInThePast))

  def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[DotaApiRepo with SttpClient, Throwable, Seq[Peer]] =
    ZIO.accessM(_.get.peers(playerId, numberOfDaysInThePast))
}
