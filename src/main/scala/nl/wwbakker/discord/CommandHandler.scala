package nl.wwbakker.discord

import nl.wwbakker.services.dota.DotaMatchesRepo.DotaMatchRepoEnv
import nl.wwbakker.services.dota.HeroRepo.HeroRepoEnv
import nl.wwbakker.services.dota.MatchStatsService.{HeroStats, MatchStatsServiceEnv}
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
        MatchStatsService.heroWinrates(stat, highestToLowest).mapError(TechnicalError.apply)
      case None =>
        ZIO.fail(UserError(s"Possible options for best/worst: " + HeroStats.possibilities.mkString(", ")))
    }

  def handleCommand(args: Seq[String], commandPrefix: String = ""): ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient, DotabotError, String] = {
    args.toList match {
      case "winloss" :: Nil =>
        MatchStatsService.winLoss.mapError(TechnicalError.apply)
      case "favorite" :: "hero" :: Nil =>
        MatchStatsService.favoriteHeroes.mapError(TechnicalError.apply)
      case "best" :: statName :: Nil =>
        topBottomHeroStats(statName, highestToLowest = true)
      case "worst" :: statName :: Nil =>
        topBottomHeroStats(statName, highestToLowest = false)
      case "latestmatches" :: Nil =>
        printLatestMatches
      case _ =>
        ZIO.fail(
          UserError(
            s"""Possible commands:
            |$commandPrefix winloss
            |$commandPrefix latestmatches
            |$commandPrefix favorite hero
            |$commandPrefix best [${HeroStats.possibilities.mkString("/")}]
            |$commandPrefix worst [${HeroStats.possibilities.mkString("/")}]
            |""".stripMargin
          )
        )
    }
  }
}
