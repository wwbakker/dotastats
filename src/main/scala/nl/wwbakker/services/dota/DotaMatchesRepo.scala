package nl.wwbakker.services.dota


import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import nl.wwbakker.misc.Utils.localDateTimeFromUnixTimestamp
import nl.wwbakker.services.dota.DotaApiRepo.{DotaApiRepo, RecentMatch}
import nl.wwbakker.services.generic.LocalStorageRepo
import nl.wwbakker.services.generic.LocalStorageRepo.LocalStorageRepo
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{Has, IO, UIO, ZIO, ZLayer}

import java.time.{Instant, LocalDateTime}
import java.util.TimeZone

object DotaMatchesRepo {
  private val numberOfGamesCutOff = 100

  case class Player(account_id: Option[Int], personaname: Option[String], hero_id: Int, win: Int, lose: Int, start_time: Int)

  case class Match(match_id: Long, start_time: Int, duration: Int, players: Seq[Player]) {
    def startTimeText: String =
      localDateTimeFromUnixTimestamp(start_time).toString

    def durationText: String =
      s"${duration / 60}:${String.format("%02d", duration % 60)}"

    def text = s"Match: $match_id, started $startTimeText, took $durationText"
  }

  type DotaMatchRepoEnv = Has[DotaMatchesRepo.Service]


  class Service(dotaApiRepo: DotaApiRepo.Service, localStorageRepo: LocalStorageRepo.Service) {
    def latestGames(playerId: Int): ZIO[SttpClient, Throwable, Seq[Match]] =
      for {
        cachedMatchesAsString        <- localStorageRepo.list()
        decodedCachedMatches         <- ZIO.foreachPar(cachedMatchesAsString)(decodeTo[Match])
        recentMatches                <- dotaApiRepo.recentMatches(playerId)
        matchIdsToRetrieve           <- nonCachedMatchIds(decodedCachedMatches, recentMatches.take(numberOfGamesCutOff))
        retrievedMatchDataAsString   <- ZIO.foreachPar(matchIdsToRetrieve)(dotaApiRepo.rawMatch)
        _                            <- ZIO.foreach(retrievedMatchDataAsString)(localStorageRepo.add)
        retrievedMatches             <- ZIO.foreachPar(retrievedMatchDataAsString)(decodeTo[Match])
      } yield (decodedCachedMatches :++ retrievedMatches).sortBy(_.start_time).reverse.take(numberOfGamesCutOff)

    private def decodeTo[A: Decoder](body: String): IO[Throwable, A] =
      IO.fromEither(decode[A](body).swap.map(new IllegalStateException(_)).swap)


    private def nonCachedMatchIds(cachedMatches: Seq[Match], recentMatches: Seq[RecentMatch]): UIO[Seq[Long]] =
      ZIO.succeed(
        recentMatches
          .filterNot(recentMatch => cachedMatches.exists(cachedMatch => cachedMatch.match_id == recentMatch.match_id))
          .map(_.match_id)
      )

  }

  val live: ZLayer[DotaApiRepo with LocalStorageRepo, Throwable, DotaMatchRepoEnv] =
    ZLayer.fromServices[DotaApiRepo.Service, LocalStorageRepo.Service, Service](
      new Service(_, _)
    )

  val dependencies: ZLayer[Any, Throwable, SttpClient with DotaApiRepo with LocalStorageRepo with DotaMatchRepoEnv] =
    (DotaApiRepo.dependencies ++ LocalStorageRepo.live) >+> DotaMatchesRepo.live

  // front-facing API
  def latestGames(playerId: Int): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Seq[Match]] =
    ZIO.accessM(_.get.latestGames(playerId))

}
