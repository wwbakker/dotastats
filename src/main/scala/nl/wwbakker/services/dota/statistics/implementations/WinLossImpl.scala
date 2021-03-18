package nl.wwbakker.services.dota.statistics.implementations

import nl.wwbakker.misc.Utils.percentage
import nl.wwbakker.services.dota.DotaMatchesRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match}
import nl.wwbakker.services.dota.statistics.model.Players
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{UIO, ZIO}

trait WinLossImpl {
  def winLoss: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    for {
      allGames <- DotaMatchesRepo.latestGames(Players.wesselId)
      results <- ZIO.foreachPar(Players.everyone)(calculateWinLoss(allGames, _))
    } yield results.mkString("\n")

  private def calculateWinLoss(matches: Seq[Match], playerId: Int): UIO[String] =
    ZIO.succeed {
      val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
        matches.flatMap(_.players.find(_.account_id.contains(playerId)))
      val wins = playerSpecificResults.count(_.win == 1)
      val total = playerSpecificResults.length
      val losses = total - wins
      val winrate = percentage(wins, wins + losses)
      val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
      s"**$playerName**: $wins wins, $losses losses - $winrate% winrate"
    }
}
