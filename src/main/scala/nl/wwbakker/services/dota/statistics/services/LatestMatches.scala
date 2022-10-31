package nl.wwbakker.services.dota.statistics.services

import nl.wwbakker.services.dota.DotaMatchesRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.Match
import nl.wwbakker.services.dota.statistics.model.Players
import zio.{UIO, ZIO, ZLayer}

object LatestMatches {

  trait Service {
    def latestMatchesOverview: ZIO[Any, Throwable, String]
  }

  case class ServiceImpl(dotaMatchesRepo: DotaMatchesRepo.Service) extends Service {
    override def latestMatchesOverview: ZIO[Any, Throwable, String] =
      for {
        allGames <- dotaMatchesRepo.latestGames(Players.wesselId)
        matchesText <- matchesText(allGames)
      } yield matchesText

    def matchesText(matches: Seq[Match]): UIO[String] = {
      ZIO.succeed(
        matches
          .map(m => s"${m.startTimeText}, ${m.durationText} - **${if (m.wesselWon) "WON" else "LOST"}**")
          .take(5)
          .mkString("\n")
      )
    }
  }

  object ServiceImpl {
    val live: ZLayer[DotaMatchesRepo.Service, Nothing, LatestMatches.Service] = ZLayer {
      for {
        dmr <- ZIO.service[DotaMatchesRepo.Service]
      } yield ServiceImpl(dmr)
    }
  }

}
