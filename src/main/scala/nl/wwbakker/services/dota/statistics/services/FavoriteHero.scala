package nl.wwbakker.services.dota.statistics.services

import nl.wwbakker.misc.Utils.percentage
import nl.wwbakker.services.dota.DotaMatchesRepo.Match
import nl.wwbakker.services.dota.HeroRepo.Hero
import nl.wwbakker.services.dota.statistics.model.Players
import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import zio.{UIO, ZIO, ZLayer}

object FavoriteHero {
  trait Service {
    def favoriteHeroes: ZIO[Any, Throwable, String]
  }

  case class ServiceImpl(dotaMatchesRepo: DotaMatchesRepo.Service, heroRepo: HeroRepo.Service) extends Service {
    def favoriteHeroes: ZIO[Any, Throwable, String] =
      for {
        allGames <- dotaMatchesRepo.latestGames(Players.wesselId)
        allHeroes <- heroRepo.heroes
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

  object ServiceImpl {
    val live: ZLayer[HeroRepo.Service & DotaMatchesRepo.Service, Nothing, FavoriteHero.Service] = ZLayer {
      for {
        dmr <- ZIO.service[DotaMatchesRepo.Service]
        hr <- ZIO.service[HeroRepo.Service]
      } yield ServiceImpl(dmr, hr)
    }
  }
}