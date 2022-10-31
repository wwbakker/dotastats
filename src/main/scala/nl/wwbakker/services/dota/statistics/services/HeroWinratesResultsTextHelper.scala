package nl.wwbakker.services.dota.statistics.services

import nl.wwbakker.services.dota.statistics.model.HeroStats.HeroStatWinrate
import zio.{UIO, ZIO}

trait HeroWinratesResultsTextHelper {
  def resultsText(heroNamesWithWinrate: Seq[(Long, Seq[HeroStatWinrate])]): UIO[String] =
    ZIO.succeed(
      heroNamesWithWinrate.map { case (winrate, heroesWithWinrates) =>
        s"__$winrate% winrate__: " + heroesWithWinrates.map(hero => s"${hero.heroName} ${hero.wins}-${hero.total - hero.wins}").mkString(", ")
      }
        .mkString("\n")
    )
}
