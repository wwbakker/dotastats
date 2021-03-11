package nl.wwbakker.discord

import nl.wwbakker.services.dota.DotaMatchesRepo.DotaMatchRepoEnv
import nl.wwbakker.services.dota.HeroRepo.HeroRepoEnv
import nl.wwbakker.services.dota.MatchStatsService.{HeroStats, MatchStatsServiceEnv, PlayerStats}
import nl.wwbakker.services.dota.{DotaMatchesRepo, MatchStatsService}
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.ZIO

object CommandHandler {

  val wesselId = 21842016

  val numberOfDaysInThePast = 14

  def printLatestMatches: ZIO[Any with DotaMatchRepoEnv with SttpClient, TechnicalError, String] = (for {
    matches <- DotaMatchesRepo.latestGames(wesselId)
  } yield matches.map(_.text).mkString("\n")
  ).mapError(TechnicalError.apply)

  def topBottomHeroStats(statName : String, highestToLowest : Boolean): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, DotabotError, String] =
    HeroStats.fromStatName(statName) match {
      case Some(stat) =>
        MatchStatsService.heroWinratesGroupedByPlayer(stat, highestToLowest).mapError(TechnicalError.apply)
      case None =>
        ZIO.fail(UserError(s"Possible options for best/worst: " + HeroStats.possibilities.mkString(", ")))
    }

  def matchStatsPlot(statName : String): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, DotabotError, Array[Byte]] =
    PlayerStats.fromStatName(statName) match {
      case Some(stat) =>
        MatchStatsService.matchStatPlot(stat).mapError(TechnicalError.apply)
      case None =>
        ZIO.fail(UserError(s"Possible options for match stats: " + PlayerStats.possibilities.mkString(", ")))
    }

  def handleCommand(args: Seq[String], commandPrefix: String = ""): ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient, DotabotError, DotabotSuccess] = {
    args.toList match {
      case "winloss" :: Nil =>
        MatchStatsService.winLoss.mapError(TechnicalError.apply).map(SuccessText)
      case "winlossplot" :: Nil =>
        MatchStatsService.winLossPlot.mapError(TechnicalError.apply).map(SuccessPicture)
      case "favorite" :: "hero" :: Nil =>
        MatchStatsService.favoriteHeroes.mapError(TechnicalError.apply).map(SuccessText)
      case "best" :: statName :: Nil =>
        topBottomHeroStats(statName, highestToLowest = true).map(SuccessText)
      case "worst" :: statName :: Nil =>
        topBottomHeroStats(statName, highestToLowest = false).map(SuccessText)
      case "friendly" :: "team" :: Nil =>
        MatchStatsService.heroWinRatesOverall(enemyTeam = false).mapError(TechnicalError.apply).map(SuccessText)
      case "enemy" :: "team" :: Nil =>
        MatchStatsService.heroWinRatesOverall(enemyTeam = true).mapError(TechnicalError.apply).map(SuccessText)
      case "plot" :: statName :: Nil =>
        matchStatsPlot(statName).map(SuccessPicture)
      case "latestmatches" :: Nil =>
        printLatestMatches.map(SuccessText)
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
}
