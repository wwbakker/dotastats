package nl.wwbakker.services.dota.statistics

import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.dota.statistics.services.{FavoriteHero, HeroWinratesGroupedByPlayer, HeroWinratesOverall, LatestMatches, MatchStatPlot, WinLoss, WinLossPlot}
import zio.ZLayer

object StatisticsServices {

  val live: ZLayer[HeroRepo.Service with DotaMatchesRepo.Service, Nothing, FavoriteHero.Service with HeroWinratesGroupedByPlayer.Service with HeroWinratesOverall.Service with LatestMatches.Service with MatchStatPlot.Service with WinLoss.Service with WinLossPlot.Service] =
    FavoriteHero.ServiceImpl.live ++ HeroWinratesGroupedByPlayer.ServiceImpl.live ++ HeroWinratesOverall.ServiceImpl.live ++
      LatestMatches.ServiceImpl.live ++ MatchStatPlot.ServiceImpl.live ++ WinLoss.ServiceImpl.live ++ WinLossPlot.ServiceImpl.live
}
