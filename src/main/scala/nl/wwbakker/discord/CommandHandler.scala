package nl.wwbakker.discord

import nl.wwbakker.services.dota.statistics.model.{HeroStats, PlayerStats}
import nl.wwbakker.services.dota.statistics.services._
import zio.{ZIO, ZLayer}

object CommandHandler {

  val wesselId = 21842016

  val numberOfDaysInThePast = 14


  trait Service {
    def handleCommand(args: Seq[String], commandPrefix: String = ""): ZIO[Any, DotabotError, DotabotSuccess]
  }

  case class ServiceImpl(
                        favoriteHero: FavoriteHero.Service,
                        heroWinratesGroupedByPlayer: HeroWinratesGroupedByPlayer.Service,
                        heroWinratesOverall: HeroWinratesOverall.Service,
                        latestMatches: LatestMatches.Service,
                        matchStatPlot: MatchStatPlot.Service,
                        winLoss: WinLoss.Service,
                        winLossPlot: WinLossPlot.Service,
                        ) extends Service {
    override def handleCommand(args: Seq[String], commandPrefix: String = ""): ZIO[Any, DotabotError, DotabotSuccess] = {
      args.toList match {
        case "winloss" :: Nil =>
          winLoss.winLoss.mapError(TechnicalError.apply).map(SuccessText.apply)
        case "winlossplot" :: Nil =>
          winLossPlot.winLossPlot.mapError(TechnicalError.apply).map(SuccessPicture.apply)
        case "favorite" :: "hero" :: Nil =>
          favoriteHero.favoriteHeroes.mapError(TechnicalError.apply).map(SuccessText.apply)
        case "best" :: statName :: Nil =>
          topBottomHeroStats(statName, highestToLowest = true).map(SuccessText.apply)
        case "worst" :: statName :: Nil =>
          topBottomHeroStats(statName, highestToLowest = false).map(SuccessText.apply)
        case "friendly" :: "team" :: Nil =>
          heroWinratesOverall.heroWinRatesOverall(enemyTeam = false).mapError(TechnicalError.apply).map(SuccessText.apply)
        case "enemy" :: "team" :: Nil =>
          heroWinratesOverall.heroWinRatesOverall(enemyTeam = true).mapError(TechnicalError.apply).map(SuccessText.apply)
        case "plot" :: statName :: Nil =>
          matchStatsPlot(statName).map(SuccessPicture.apply)
        case "latestmatches" :: Nil =>
          latestMatches.latestMatchesOverview.mapError(TechnicalError.apply).map(SuccessText.apply)
        case _ =>
          ZIO.fail(
            UserError(
              s"""Possible commands:
                 |$commandPrefix **winloss**
                 |$commandPrefix **winlossplot**
                 |$commandPrefix **latestmatches**
                 |$commandPrefix **favorite hero**
                 |$commandPrefix **best [${HeroStats.possibilities.mkString("/")}]**
                 |$commandPrefix **worst [${HeroStats.possibilities.mkString("/")}]**
                 |$commandPrefix **friendly team** - *winrates per hero, when they are in our team (high winrate means they are a good pick)*
                 |$commandPrefix **enemy team** - *winrates per hero, when they are in their team (high winrate means they are a good ban)*
                 |$commandPrefix **plot [${PlayerStats.possibilities.mkString("/")}**
                 |""".stripMargin
            )
          )
      }
    }


    private def topBottomHeroStats(statName: String, highestToLowest: Boolean): ZIO[Any, DotabotError, String] =
      HeroStats.fromStatName(statName) match {
        case Some(stat) =>
          heroWinratesGroupedByPlayer.heroWinRatesGroupedByPlayer(stat, highestToLowest).mapError(TechnicalError.apply)
        case None =>
          ZIO.fail(UserError(s"Possible options for best/worst: " + HeroStats.possibilities.mkString(", ")))
      }

    private def matchStatsPlot(statName: String): ZIO[Any, DotabotError, Array[Byte]] =
      PlayerStats.fromStatName(statName) match {
        case Some(stat) =>
          matchStatPlot.matchStatPlot(stat).mapError(TechnicalError.apply)
        case None =>
          ZIO.fail(UserError(s"Possible options for match stats: " + PlayerStats.possibilities.mkString(", ")))
      }
  }

  object ServiceImpl {
    val live: ZLayer[WinLossPlot.Service & WinLoss.Service & MatchStatPlot.Service & LatestMatches.Service & HeroWinratesOverall.Service & HeroWinratesGroupedByPlayer.Service & FavoriteHero.Service, Nothing, CommandHandler.Service] =
      ZLayer {
        for {
          favoriteHero <- ZIO.service[FavoriteHero.Service]
          heroWinratesGroupedByPlayer <- ZIO.service[HeroWinratesGroupedByPlayer.Service]
          heroWinratesOverall <- ZIO.service[HeroWinratesOverall.Service]
          latestMatches <- ZIO.service[LatestMatches.Service]
          matchStatPlot <- ZIO.service[MatchStatPlot.Service]
          winLoss <- ZIO.service[WinLoss.Service]
          winLossPlot <- ZIO.service[WinLossPlot.Service]
        } yield new ServiceImpl(favoriteHero, heroWinratesGroupedByPlayer, heroWinratesOverall, latestMatches, matchStatPlot, winLoss, winLossPlot)
      }
  }
}
