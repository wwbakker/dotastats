package nl.wwbakker.services.dota.statistics

import nl.wwbakker.services.dota.DotaApiRepo.DotaApiRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.DotaMatchRepoEnv
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.dota.statistics.implementations._
import nl.wwbakker.services.dota.statistics.model._
import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.generic.LocalStorageRepo.LocalStorageRepo
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{Has, ULayer, ZIO, ZLayer}

object MatchStatsService {
  // API

  type MatchStatsServiceEnv = Has[MatchStatsService.Service]

  trait Service {
    def winLoss: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String]

    def winLossPlot: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]]

    def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def heroWinRatesGroupedByPlayer(heroStat: Hero => String, highestToLowest: Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def heroWinRatesOverall(enemyTeam: Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def matchStatPlot(stat: PlayerStats.PlayerStatInfo): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]]

    def latestMatchesOverview: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String]
  }

  val live: ULayer[MatchStatsServiceEnv] =
    ZLayer.succeed(
      new Service with WinLossImpl with WinLossPlotImpl with MatchStatPlotImpl with FavoriteHeroImpl with HeroWinratesOverallImpl with HeroWinratesGroupedByPlayerImpl with LatestMatchesImpl
    )

  val dependencies: ZLayer[Any, Throwable, HeroRepoEnv with SttpClient with DotaApiRepo with LocalStorageRepo with DotaMatchRepoEnv] =
    HeroRepo.live ++ DotaMatchesRepo.dependencies

  def winLoss: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    ZIO.accessM(_.get.winLoss)

  def winLossPlot: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
    ZIO.accessM(_.get.winLossPlot)

  def favoriteHeroes: ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.favoriteHeroes)

  def heroWinratesGroupedByPlayer(heroStat: HeroStats.HeroStatGetter, highestToLowest: Boolean): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.heroWinRatesGroupedByPlayer(heroStat, highestToLowest))

  def heroWinRatesOverall(enemyTeam: Boolean): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.heroWinRatesOverall(enemyTeam))

  def matchStatPlot(stat: PlayerStats.PlayerStatInfo): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
    ZIO.accessM(_.get.matchStatPlot(stat))

  def latestMatchesOverview: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    ZIO.accessM(_.get.latestMatchesOverview)

}
