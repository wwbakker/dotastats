package nl.wwbakker.discord

import nl.wwbakker.services.dota.DotaMatchesRepo.DotaMatchRepoEnv
import nl.wwbakker.services.dota.HeroRepo.HeroRepoEnv
import nl.wwbakker.services.dota.MatchStatsService.MatchStatsServiceEnv
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

  def handleCommand(args: Seq[String], commandPrefix: String = ""): ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient, Object, String] = {
    args.toList match {
      case "winloss" :: Nil =>
        MatchStatsService.winLoss
      case "favheroes" :: Nil =>
        MatchStatsService.favoriteHeroes
      case "lowestwinrateheroes" :: Nil =>
        MatchStatsService.lowestWinrateHeroes
      case "highestwinrateheroes" :: Nil =>
        MatchStatsService.highestWinrateHeroes
      case "latestmatches" :: Nil =>
        printLatestMatches
      case _ =>
        ZIO.fail(
          UserError(
            s"""Possible commands:
            |$commandPrefix winloss
            |$commandPrefix latestmatches
            |$commandPrefix favheroes
            |$commandPrefix lowestwinrateheroes
            |$commandPrefix highestwinrateheroes
            |""".stripMargin
          )
        )
    }
  }
}
