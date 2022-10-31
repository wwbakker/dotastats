package nl.wwbakker.services.dota

import nl.wwbakker.services.dota.Clients.SttpClient
import sttp.client3.{Response, UriContext, basicRequest}
import sttp.model.Uri
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
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

  case class RecentMatch(match_id: Long, player_slot: Int, radiant_win: Option[Boolean], hero_id: Int,
                         start_time: Int, duration: Int, game_mode: Int, lobby_type: Int,
                         kills: Int, deaths: Int, assists: Int, skill: Option[Int],
                         leaver_status: Int, party_size: Option[Int])

  // Service description
  trait Service {
    def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[Any, Throwable, WinLoss]

    def peers(playerId: Int, numberOfDaysInThePast: Int = 14): ZIO[Any, Throwable, Seq[Peer]]

    def recentMatches(playerId: Int) : ZIO[Any, Throwable, Seq[RecentMatch]]

    def rawMatch(matchId : Long) : ZIO[Any, Throwable, String]

    def rawHeroes : ZIO[Any, Throwable, String]
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

    override def rawHeroes: ZIO[Any, Throwable, String] =
      callService(sttpClient, uri"https://api.opendota.com/api/heroes")

    private def callService(sttpClient: SttpClient, uri: Uri): ZIO[Any, Throwable, String] =
      for {
        response <- sttpClient.send(basicRequest.get(uri))
        response200 <- filter200Response(response)
      } yield response200

    private implicit val decoderWinLoss: JsonDecoder[WinLoss] = DeriveJsonDecoder.gen[WinLoss]
    private implicit val decoderPeer: JsonDecoder[Peer] = DeriveJsonDecoder.gen[Peer]
    private implicit val decoderRecentMatch: JsonDecoder[RecentMatch] = DeriveJsonDecoder.gen[RecentMatch]
    private def callServiceAndDecode[A: JsonDecoder](sttpClient: SttpClient, uri: Uri): ZIO[Any, Throwable, A] =
      for {
        textResponse <- callService(sttpClient, uri)
        decoded <- decodeTo[A](textResponse)
      } yield decoded

    private def filter200Response(response: Response[Either[String, String]]): IO[Throwable, String] =
      ZIO.fromEither(response.body.swap.map(e =>
        new IllegalStateException(s"Received error from service:\n $e")).swap)

    private def decodeTo[A: JsonDecoder](body: String): IO[Throwable, A] =
      ZIO.fromEither(body.fromJson[A].swap.map(new IllegalStateException(_)).swap)

  }

  object ServiceImpl {
    val live: ZLayer[SttpClient, Nothing, DotaApiRepo.Service] =
      ZLayer {
        for {
          client <- ZIO.service[SttpClient]
        } yield new ServiceImpl(client)
      }
  }
}
