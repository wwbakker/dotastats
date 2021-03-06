package nl.wwbakker.services.dota

import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient, send}
import sttp.client3.{Response, UriContext, basicRequest}
import sttp.model.Uri
import zio.{Has, IO, ZIO, ZLayer}

object DotaApiRepo {

  // Responses
  case class WinLoss(win: Int, lose: Int) {
    def rate: Double = (win.toDouble / (win.toDouble + lose.toDouble)) * 100d
  }

  case class Peer(account_id: Long, last_played: Int,
                  win: Int, games: Int,
                  with_win: Int, with_games: Int,
                  against_win: Int, against_games: Int,
                  with_gpm_sum: Int, with_xpm_sum: Int,
                  personaname: Option[String], name: Option[String],
                  is_contributor: Boolean, last_login: Option[String],
                  avatar: Option[String], avatarfull: Option[String])

  case class RecentMatch(match_id: Long, player_slot: Int, radiant_win: Boolean, hero_id: Int,
                         start_time: Int, duration: Int, game_mode: Int, lobby_type: Int,
                         kills: Int, deaths: Int, assists: Int, skill: Option[Int],
                         leaver_status: Int, party_size: Option[Int])

  // Service description
  type DotaApiRepo = Has[DotaApiRepo.Service]

  trait Service {
    def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[SttpClient, Throwable, WinLoss]

    def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[SttpClient, Throwable, Seq[Peer]]

    def recentMatches(playerId: Int) : ZIO[SttpClient, Throwable, Seq[RecentMatch]]

    def rawMatch(matchId : Long) : ZIO[SttpClient, Throwable, String]
  }

  // implementation
  val live: ZLayer[SttpClient, Nothing, DotaApiRepo] = ZLayer.succeed(
    new Service {
      override def winLoss(playerId: Int, numberOfDaysInThePast: Int): ZIO[SttpClient, Throwable, WinLoss] =
        callServiceAndDecode[WinLoss](uri"https://api.opendota.com/api/players/$playerId/wl?significant=0&date=$numberOfDaysInThePast")

      override def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[SttpClient, Throwable, Seq[Peer]] =
        callServiceAndDecode[Seq[Peer]](uri"https://api.opendota.com/api/players/$playerId/peers?significant=0&date=$numberOfDaysInThePast")

      override def recentMatches(playerId: Int): ZIO[SttpClient, Throwable, Seq[RecentMatch]] =
        callServiceAndDecode[Seq[RecentMatch]](uri"https://api.opendota.com/api/players/$playerId/matches?significant=0")

      override def rawMatch(matchId : Long) : ZIO[SttpClient, Throwable, String] =
        callService(uri"https://api.opendota.com/api/matches/$matchId")
    }
  )

  val dependencies: ZLayer[Any, Throwable, SttpClient with DotaApiRepo] =
    AsyncHttpClientZioBackend.layer() >+> DotaApiRepo.live

  private def callService(uri : Uri): ZIO[SttpClient, Throwable, String] =
    for {
      response <- send(basicRequest.get(uri))
      response200 <- filter200Response(response)
    } yield response200

  private def callServiceAndDecode[A: Decoder](uri: Uri): ZIO[SttpClient, Throwable, A] =
    for {
      textResponse <- callService(uri)
      decoded <- decodeTo[A](textResponse)
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

  def recentMatches(playerId: Int): ZIO[DotaApiRepo with SttpClient, Throwable, Seq[RecentMatch]] =
    ZIO.accessM(_.get.recentMatches(playerId))

  def rawMatch(matchId : Int) : ZIO[DotaApiRepo with SttpClient, Throwable, String] =
    ZIO.accessM(_.get.rawMatch(matchId))

}
