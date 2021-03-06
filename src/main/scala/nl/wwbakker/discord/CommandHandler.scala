package nl.wwbakker.discord

import nl.wwbakker.services.dota.{DotaApiRepo, DotaMatchesRepo, MatchStatsService, VisualizationService}
import nl.wwbakker.services.dota.DotaApiRepo.DotaApiRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.DotaMatchRepoEnv
import nl.wwbakker.services.dota.HeroRepo.HeroRepoEnv
import nl.wwbakker.services.dota.MatchStatsService.MatchStatsServiceEnv
import nl.wwbakker.services.dota.VisualizationService.NamedWinLoss
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio.console.Console
import zio.{ZIO, ZLayer}

object CommandHandler {

  val wesselId = 21842016
  val chairmanId = 55277122
  // val olivierId = null, met zelfde party id

  val numberOfDaysInThePast = 14

//  def printWinLoss: ZIO[DotaApiRepo with SttpClient, DotabotError, String] = (for {
//    wessel <- DotaApiRepo.winLoss(wesselId).map(NamedWinLoss.from(_, "Wessel"))
//    peers <- DotaApiRepo.peers(wesselId).map(_.map(NamedWinLoss.from))
//    text <- VisualizationService.listWinLoss(peers.prepended(wessel))
//  } yield "Results last 14 days:\n" + text)
//    .mapError(TechnicalError.apply)



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
      case "latestmatches" :: Nil =>
        printLatestMatches
      case _ =>
        ZIO.fail(
          UserError(
            s"""Possible commands:
                $commandPrefix winloss
                $commandPrefix latestmatches
                $commandPrefix favheroes
             """.stripMargin
          )
        )
    }
  }
}
