package nl.wwbakker.services.dota.statistics.implementations

import nl.wwbakker.services.dota.DotaMatchesRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match}
import nl.wwbakker.services.dota.statistics.model.Players
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{UIO, ZIO}

trait LatestMatchesImpl {

  def latestMatchesOverview: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    for {
      allGames <- DotaMatchesRepo.latestGames(Players.wesselId)
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
