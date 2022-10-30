package nl.wwbakker.services.dota.statistics.implementations

import nl.wwbakker.misc.Utils.percentage
import nl.wwbakker.services.dota.Clients.SttpClient
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match, Player}
import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.dota.statistics.model.HeroStats.HeroStatWinrate
import nl.wwbakker.services.dota.statistics.model.{HeroStats, Players}
import zio.{UIO, ZIO}

trait HeroWinratesGroupedByPlayerImpl extends HeroWinratesResultsTextImpl {

  def heroWinRatesGroupedByPlayer(heroStat: HeroStats.HeroStatGetter, highestToLowest: Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    for {
      allGames <- DotaMatchesRepo.latestGames(Players.wesselId)
      allHeroes <- HeroRepo.heroes
      results <- ZIO.foreachPar(Players.everyone)(heroWinrateForPlayer(allGames, allHeroes, _, heroStat, highestToLowest))
    } yield results.mkString("\n")

  private def heroWinrateForPlayer(matches: Seq[Match], heroes: Seq[Hero], playerId: Int, heroStat: HeroStats.HeroStatGetter, highToLow: Boolean): ZIO[Any, Nothing, String] = for {
    playerSpecificResults <- playerSpecificResults(matches, playerId)
    heroesWinsLosses <- heroStatWinsLosses(playerSpecificResults, heroes, heroStat)
    heroesWinsLossesGrouped <- groupByWinrate(heroesWinsLosses)
    topOrBottom5 <- if (highToLow) top5(heroesWinsLossesGrouped)(_._1)
    else bottom5(heroesWinsLossesGrouped)(_._1)
    resultsPerHero <- resultsText(topOrBottom5)
    resultsPerHeroWithPlayerName <- addPlayerName(resultsPerHero, playerSpecificResults)
  } yield resultsPerHeroWithPlayerName


  private def playerSpecificResults(matches: Seq[Match], playerId: Int): UIO[Seq[Player]] =
    ZIO.succeed(matches.flatMap(_.players.find(_.account_id.contains(playerId))))

  private def heroStatWinsLosses(playerSpecificResults: Seq[Player], heroes: Seq[Hero], heroStatGetter: HeroStats.HeroStatGetter): UIO[Seq[HeroStatWinrate]] =
    ZIO.succeed {
      // for example: ("agi" -> Seq(heroId3, heroId6, heroId7))
      val heroesGroupedPerStatValue = playerSpecificResults.map(_.hero_id).groupBy(heroId => resolveHeroStat(heroes, heroId, heroStatGetter))
      heroesGroupedPerStatValue.map { case (heroStatValue, heroIdsThatMatchStat) =>
        val numberOfTimesPlayed = playerSpecificResults.count(psr => heroIdsThatMatchStat.contains(psr.hero_id))
        val numberOfTimesWon = playerSpecificResults.filter(_.win == 1).count(psr => heroIdsThatMatchStat.contains(psr.hero_id))
        val winrate = percentage(numberOfTimesWon, numberOfTimesPlayed)
        HeroStatWinrate(heroStatValue, numberOfTimesWon, numberOfTimesPlayed, winrate)
      }.toSeq
    }

  private def groupByWinrate(heroWinrates: Seq[HeroStatWinrate]): UIO[Seq[(Long, Seq[HeroStatWinrate])]] =
    ZIO.succeed(heroWinrates.groupBy(_.winrate).toSeq)

  private def top5[A, B](results: Seq[A])(f: A => B)(implicit ord: Ordering[B]): UIO[Seq[A]] =
    ZIO.succeed(results.sortBy(f).reverse.take(5))

  private def bottom5[A, B](results: Seq[A])(f: A => B)(implicit ord: Ordering[B]): UIO[Seq[A]] =
    ZIO.succeed(results.sortBy(f).take(5))

  private def resolveHeroStat(heroes: Seq[Hero], heroId: Int, stat: Hero => String): String =
    heroes.find(_.id == heroId).map(stat).getOrElse("Unknown")

  private def addPlayerName(resultsText: String, playerSpecificResults: Seq[Player]): UIO[String] =
    ZIO.succeed {
      val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
      s"""**$playerName**
         |$resultsText""".stripMargin
    }
}
