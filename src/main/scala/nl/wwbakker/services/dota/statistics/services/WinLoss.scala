package nl.wwbakker.services.dota.statistics.services

import nl.wwbakker.misc.Utils.percentage
import nl.wwbakker.services.dota.DotaMatchesRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.Match
import nl.wwbakker.services.dota.statistics.model.Players
import zio.{UIO, ZIO, ZLayer}

object WinLoss {
  trait Service {
    def winLoss: ZIO[Any, Throwable, String]
  }

  case class ServiceImpl(dotaMatchesRepo: DotaMatchesRepo.Service) extends Service {
    override def winLoss: ZIO[Any, Throwable, String] =
      for {
        allGames <- dotaMatchesRepo.latestGames(Players.wesselId)
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

  object ServiceImpl {
    val live: ZLayer[DotaMatchesRepo.Service, Nothing, WinLoss.Service] = ZLayer {
      for {
        dmr <- ZIO.service[DotaMatchesRepo.Service]
      } yield ServiceImpl(dmr)
    }
  }
}
