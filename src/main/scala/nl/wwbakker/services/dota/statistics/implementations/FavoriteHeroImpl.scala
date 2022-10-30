package nl.wwbakker.services.dota.statistics.implementations

import nl.wwbakker.misc.Utils.percentage
import nl.wwbakker.services.dota.Clients.SttpClient
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match}
import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.dota.statistics.model.Players
import zio.{UIO, ZIO}

trait FavoriteHeroImpl {
  def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    for {
      allGames <- DotaMatchesRepo.latestGames(Players.wesselId)
      allHeroes <- HeroRepo.heroes
      results <- ZIO.foreachPar(Players.everyone)(calculateFavoriteHeroes(allGames, allHeroes, _))
    } yield results.mkString("\n")

  private def calculateFavoriteHeroes(matches: Seq[Match], heroes: Seq[Hero], playerId: Int): UIO[String] =
    ZIO.succeed {
      val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
        matches.flatMap(_.players.find(_.account_id.contains(playerId)))
      val playedHeroes = playerSpecificResults.map(_.hero_id).distinct
      val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")

      val results = playedHeroes
        .map(heroId => (heroId, playerSpecificResults.count(_.hero_id == heroId)))
        .sortBy(_._2).reverse
        .take(5)
        .map { case (heroId, count) => (heroes.find(_.id == heroId).map(_.localized_name).getOrElse("Unknown hero"), count) }
        .map { case (heroName, count) => s"$heroName: $count times (${percentage(count, playerSpecificResults.length)}% of games)" }
        .mkString("\n")

      s"""**$playerName**:
         |$results""".stripMargin
    }
}
