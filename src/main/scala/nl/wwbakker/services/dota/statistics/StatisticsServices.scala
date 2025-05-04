package nl.wwbakker.services.dota.statistics

import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.dota.statistics.services.{FavoriteHero, HeroWinratesGroupedByPlayer, HeroWinratesOverall, LatestMatches, MatchStatPlot, WinLoss, WinLossPlot}
import zio.ZLayer

object StatisticsServices {

  val live: ZLayer[HeroRepo.Service & DotaMatchesRepo.Service, Nothing, FavoriteHero.Service & HeroWinratesGroupedByPlayer.Service & HeroWinratesOverall.Service & LatestMatches.Service & MatchStatPlot.Service & WinLoss.Service & WinLossPlot.Service] =
    FavoriteHero.ServiceImpl.live ++ HeroWinratesGroupedByPlayer.ServiceImpl.live ++ HeroWinratesOverall.ServiceImpl.live ++
      LatestMatches.ServiceImpl.live ++ MatchStatPlot.ServiceImpl.live ++ WinLoss.ServiceImpl.live ++ WinLossPlot.ServiceImpl.live
}
