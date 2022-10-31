package nl.wwbakker.services.dota

import nl.wwbakker.misc.Utils.localDateTimeFromUnixTimestamp
import nl.wwbakker.services.dota.DotaApiRepo.RecentMatch
import nl.wwbakker.services.dota.statistics.model.Players
import nl.wwbakker.services.generic.LocalStorageRepo
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.{IO, UIO, ZIO, ZLayer}

object DotaMatchesRepo {
  private val numberOfGamesCutOff = 100

  case class Player(account_id: Option[Int], personaname: Option[String], hero_id: Int, win: Int, lose: Int, start_time: Int, kills: Int, deaths: Int, assists: Int, total_gold:Int, total_xp: Int, last_hits: Int, denies: Int, duration: Int)

  case class Match(match_id: Long, start_time: Int, duration: Int, players: Seq[Player]) {
    def startTimeText: String =
      localDateTimeFromUnixTimestamp(start_time).toString

    def durationText: String =
      s"${duration / 60}:${String.format("%02d", duration % 60)}"

    def text = s"Match: $match_id, started $startTimeText, took $durationText"

    def wesselWon: Boolean = players.find(_.account_id.contains(Players.wesselId)).exists(_.win == 1)
  }

  trait Service {
    def latestGames(playerId: Int): ZIO[Any, Throwable, Seq[Match]]
  }


  case class ServiceImpl(dotaApiRepo: DotaApiRepo.Service, localStorageRepo: LocalStorageRepo.Service) extends Service {
    def latestGames(playerId: Int): ZIO[Any, Throwable, Seq[Match]] =
      for {
        cachedMatchesAsString         <- localStorageRepo.listMatches()
        decodedCachedMatches          <- ZIO.foreachPar(cachedMatchesAsString)(decodeTo[Match])
        recentMatches                 <- dotaApiRepo.recentMatches(playerId)
        matchIdsToRetrieve            <- nonCachedMatchIds(decodedCachedMatches, recentMatches.take(numberOfGamesCutOff))
        tryRetrievedMatchDataAsString <- ZIO.foreachPar(matchIdsToRetrieve)(id => dotaApiRepo.rawMatch(id).option)
        retrievedMatchDataAsString    = tryRetrievedMatchDataAsString.collect{ case Some(matchData) => matchData }
        _                             <- ZIO.foreach(retrievedMatchDataAsString)(localStorageRepo.addMatch)
        retrievedMatches              <- ZIO.foreachPar(retrievedMatchDataAsString)(decodeTo[Match])
      } yield (decodedCachedMatches :++ retrievedMatches).sortBy(_.start_time).reverse.take(numberOfGamesCutOff)


    private implicit val decoderPlayer: JsonDecoder[Player] = DeriveJsonDecoder.gen[Player]
    private implicit val decoderMatch: JsonDecoder[Match] = DeriveJsonDecoder.gen[Match]

    private def decodeTo[A: JsonDecoder](body: String): IO[Throwable, A] =
      ZIO.fromEither(body.fromJson[A].swap.map(new IllegalStateException(_)).swap)

    private def nonCachedMatchIds(cachedMatches: Seq[Match], recentMatches: Seq[RecentMatch]): UIO[Seq[Long]] =
      ZIO.succeed(
        recentMatches
          .filterNot(recentMatch => cachedMatches.exists(cachedMatch => cachedMatch.match_id == recentMatch.match_id))
          .map(_.match_id)
      )

  }

  object ServiceImpl {
    val live: ZLayer[DotaApiRepo.Service with LocalStorageRepo.Service, Nothing, DotaMatchesRepo.Service] =
      ZLayer {
        for {
          dar <- ZIO.service[DotaApiRepo.Service]
          lsr <- ZIO.service[LocalStorageRepo.Service]
        } yield ServiceImpl(dar, lsr)
      }
  }
}
