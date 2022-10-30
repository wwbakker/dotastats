package nl.wwbakker.services.dota

import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import nl.wwbakker.services.dota.Clients.SttpClient
import sttp.client3.{Response, UriContext, basicRequest}
import sttp.model.Uri
import zio.{IO, ZIO, ZLayer}

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
  trait Service {
    def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[Any, Throwable, WinLoss]

    def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[Any, Throwable, Seq[Peer]]

    def recentMatches(playerId: Int) : ZIO[Any, Throwable, Seq[RecentMatch]]

    def rawMatch(matchId : Long) : ZIO[Any, Throwable, String]
  }

  // implementation
  case class ServiceImpl(sttpClient: SttpClient) extends Service {
      override def winLoss(playerId: Int, numberOfDaysInThePast: Int): ZIO[Any, Throwable, WinLoss] =
        callServiceAndDecode[WinLoss](sttpClient, uri"https://api.opendota.com/api/players/$playerId/wl?significant=0&date=$numberOfDaysInThePast")

      override def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[Any, Throwable, Seq[Peer]] =
        callServiceAndDecode[Seq[Peer]](sttpClient, uri"https://api.opendota.com/api/players/$playerId/peers?significant=0&date=$numberOfDaysInThePast")

      override def recentMatches(playerId: Int): ZIO[Any, Throwable, Seq[RecentMatch]] =
        callServiceAndDecode[Seq[RecentMatch]](sttpClient, uri"https://api.opendota.com/api/players/$playerId/matches?significant=0")

      override def rawMatch(matchId : Long) : ZIO[Any, Throwable, String] =
        callService(sttpClient, uri"https://api.opendota.com/api/matches/$matchId")


    private def callService(sttpClient: SttpClient, uri: Uri): ZIO[Any, Throwable, String] =
      for {
        response <- sttpClient.send(basicRequest.get(uri))
        response200 <- filter200Response(response)
      } yield response200

    private def callServiceAndDecode[A: Decoder](sttpClient: SttpClient, uri: Uri): ZIO[Any, Throwable, A] =
      for {
        textResponse <- callService(sttpClient, uri)
        decoded <- decodeTo[A](textResponse)
      } yield decoded

    private def filter200Response(response: Response[Either[String, String]]): IO[Throwable, String] =
      ZIO.fromEither(response.body.swap.map(e =>
        new IllegalStateException(s"Received error from service:\n $e")).swap)

    private def decodeTo[A: Decoder](body: String): IO[Throwable, A] =
      ZIO.fromEither(decode[A](body).swap.map(new IllegalStateException(_)).swap)

  }

  object ServiceImpl {
    val live: ZLayer[SttpClient, Nothing, DotaApiRepo.Service] =
      ZLayer {
        for {
          client <- ZIO.service[SttpClient]
        } yield new ServiceImpl(client)
      }
  }


  // API
  def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[DotaApiRepo.Service, Throwable, WinLoss] =
    ZIO.environmentWithZIO(_.get.winLoss(playerId, numberOfDaysInThePast))

  def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[DotaApiRepo.Service, Throwable, Seq[Peer]] =
    ZIO.environmentWithZIO(_.get.peers(playerId, numberOfDaysInThePast))

  def recentMatches(playerId: Int): ZIO[DotaApiRepo.Service, Throwable, Seq[RecentMatch]] =
    ZIO.environmentWithZIO(_.get.recentMatches(playerId))

  def rawMatch(matchId : Int) : ZIO[DotaApiRepo.Service, Throwable, String] =
    ZIO.environmentWithZIO(_.get.rawMatch(matchId))

}
