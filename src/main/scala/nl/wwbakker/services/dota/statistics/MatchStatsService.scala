//package nl.wwbakker.services.dota.statistics
//
//import nl.wwbakker.services.dota.Clients.SttpClient
//import nl.wwbakker.services.dota.DotaApiRepo.DotaApiRepo
//import nl.wwbakker.services.dota.DotaMatchesRepo.DotaMatchRepoEnv
//import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
//import nl.wwbakker.services.dota.statistics.services._
//import nl.wwbakker.services.dota.statistics.model._
//import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
//import nl.wwbakker.services.generic.LocalStorageRepo.LocalStorageRepo
//import zio.{ULayer, ZIO, ZLayer}
//
//object MatchStatsService {
//  // API
//
//  type MatchStatsServiceEnv = MatchStatsService.Service
//
//  trait Service {
//    def winLoss: ZIO[Any, Throwable, String]
//
//    def winLossPlot: ZIO[Any, Throwable, Array[Byte]]
//
//    def favoriteHeroes: ZIO[Any, Throwable, String]
//
//    def heroWinRatesGroupedByPlayer(heroStat: Hero => String, highestToLowest: Boolean): ZIO[Any, Throwable, String]
//
//    def heroWinRatesOverall(enemyTeam: Boolean): ZIO[Any, Throwable, String]
//
//    def matchStatPlot(stat: PlayerStats.PlayerStatInfo): ZIO[Any, Throwable, Array[Byte]]
//
//    def latestMatchesOverview: ZIO[Any, Throwable, String]
//  }
//
//  case class ServiceImpl(winLossImpl: WinLossImpl) {
//
//  }
//
//  val live: ULayer[MatchStatsServiceEnv] =
//    ZLayer.succeed(
//      new Service with WinLossImpl with WinLossPlotImpl with MatchStatPlotImpl with FavoriteHeroImpl with HeroWinratesOverallImpl with HeroWinratesGroupedByPlayerImpl with LatestMatchesImpl
//    )
//
//
//  def winLoss: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
//    ZIO.environmentWithZIO(_.get.winLoss)
//
//  def winLossPlot: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
//    ZIO.environmentWithZIO(_.get.winLossPlot)
//
//  def favoriteHeroes: ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
//    ZIO.environmentWithZIO(_.get.favoriteHeroes)
//
//  def heroWinratesGroupedByPlayer(heroStat: HeroStats.HeroStatGetter, highestToLowest: Boolean): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
//    ZIO.environmentWithZIO(_.get.heroWinRatesGroupedByPlayer(heroStat, highestToLowest))
//
//  def heroWinRatesOverall(enemyTeam: Boolean): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
//    ZIO.environmentWithZIO(_.get.heroWinRatesOverall(enemyTeam))
//
//  def matchStatPlot(stat: PlayerStats.PlayerStatInfo): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
//    ZIO.environmentWithZIO(_.get.matchStatPlot(stat))
//
//  def latestMatchesOverview: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
//    ZIO.environmentWithZIO(_.get.latestMatchesOverview)
//
//}
