package nl.wwbakker.services.dota

import nl.wwbakker.services.dota.DotaApiRepo.DotaApiRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match, Player}
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.dota.MatchStatsService.HeroStats.HeroStat
import nl.wwbakker.services.generic.LocalStorageRepo.LocalStorageRepo
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{Has, UIO, ULayer, URIO, ZIO, ZLayer}

object MatchStatsService {
  private val wesselId = 21842016
  private val nikolaId = 39067906
  private val chairmanId = 55277122
  private val beamcannon = 56624400
  private val buzzKillington = 66268281

  private val matchSubjects = Seq(wesselId, chairmanId, beamcannon, buzzKillington)

  // API

  type MatchStatsServiceEnv = Has[MatchStatsService.Service]

  trait Service {
    def winLoss: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String]

    def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def heroWinRates(heroStat: Hero => String, highestToLowest : Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]
  }

  object HeroStats {
    type HeroStat = Hero => String
    def name : HeroStat = _.localized_name
    def primaryAttribute : HeroStat = _.primary_attr
    def attackType : HeroStat = _.attack_type
    def numberOfLegs : HeroStat = _.legs.toString

    private val statsMap = Map(
      "hero" -> name,
      "primaryAttribute" -> primaryAttribute,
      "attackType" -> attackType,
      "numberOfLegs" -> numberOfLegs
    )

    def fromStatName(statName : String) : Option[HeroStat] = statsMap.get(statName)
    val possibilities: Seq[String] = statsMap.keys.toSeq
  }

  val live: ULayer[MatchStatsServiceEnv] =
    ZLayer.succeed(
      new Service {
        override def winLoss: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            results <- ZIO.foreachPar(matchSubjects)(calculateWinLoss(allGames, _))
          } yield results.mkString("\n")


        override def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- ZIO.foreachPar(matchSubjects)(calculateFavoriteHeroes(allGames,allHeroes,_))
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

        override def heroWinRates(heroStat: HeroStat, highestToLowest : Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- ZIO.foreachPar(matchSubjects)(heroWinrateForPlayer(allGames, allHeroes, _, heroStat, highestToLowest))
          } yield results.mkString("\n")

        private def heroWinrateForPlayer(matches : Seq[Match], heroes : Seq[Hero], playerId: Int, heroStat : HeroStat, highToLow : Boolean) : ZIO[Any, Nothing, String] = for {
          playerSpecificResults         <- playerSpecificResults(matches, playerId)
          heroesWinsLosses              <- heroStatWinsLosses(playerSpecificResults, heroes, heroStat)
          heroesWinsLossesGrouped       <- groupByWinrate(heroesWinsLosses)
          topOrBottom5                  <- if (highToLow) top5(heroesWinsLossesGrouped)(_._1)
                                           else bottom5(heroesWinsLossesGrouped)(_._1)
          resultsPerHero                <- resultsText(topOrBottom5)
          resultsPerHeroWithPlayerName  <- addPlayerName(resultsPerHero, playerSpecificResults)
        } yield resultsPerHeroWithPlayerName

        private def playerSpecificResults(matches : Seq[Match], playerId : Int): UIO[Seq[Player]] =
          ZIO.succeed(matches.flatMap(_.players.find(_.account_id.contains(playerId))))

        private def heroStatWinsLosses(playerSpecificResults: Seq[Player], heroes : Seq[Hero], heroStat : HeroStat): UIO[Seq[HeroStatWinrate]] =
          ZIO.succeed {
            // for example: ("agi" -> Seq(heroId3, heroId6, heroId7))
            val heroesGroupedPerStatValue = playerSpecificResults.map(_.hero_id).groupBy(heroId => resolveHeroStat(heroes, heroId, heroStat))
            heroesGroupedPerStatValue.map{ case (heroStatValue, heroIdsThatMatchStat) =>
              val numberOfTimesPlayed = playerSpecificResults.count(psr => heroIdsThatMatchStat.contains(psr.hero_id))
              val numberOfTimesWon = playerSpecificResults.filter(_.win == 1).count(psr => heroIdsThatMatchStat.contains(psr.hero_id))
              val winrate = percentage(numberOfTimesWon, numberOfTimesPlayed)
              HeroStatWinrate(heroStatValue, numberOfTimesWon, numberOfTimesPlayed, winrate)
            }.toSeq
          }

        private def percentage(part: Int, total : Int) : Long =
          Math.round((part.toDouble / total.toDouble) * 100d)

        private def groupByWinrate(heroWinrates: Seq[HeroStatWinrate]): UIO[Seq[(Long, Seq[HeroStatWinrate])]] =
          ZIO.succeed(heroWinrates.groupBy(_.winrate).toSeq)

        private def top5[A, B](results: Seq[A])(f: A => B)(implicit ord: Ordering[B]): UIO[Seq[A]] =
          ZIO.succeed(results.sortBy(f).reverse.take(5))

        private def bottom5[A, B](results: Seq[A])(f: A => B)(implicit ord: Ordering[B]): UIO[Seq[A]]  =
          ZIO.succeed(results.sortBy(f).take(5))

        private def resolveHeroStat(heroes : Seq[Hero], heroId : Int, stat : Hero => String) : String =
          heroes.find(_.id == heroId).map(stat).getOrElse("Unknown")


        case class HeroStatWinrate(heroName: String, wins : Int, total : Int, winrate: Long)

        private def resultsText(heroNamesWithWinrate: Seq[(Long, Seq[HeroStatWinrate])]): UIO[String] =
          ZIO.succeed(
            heroNamesWithWinrate.map{case (winrate, heroesWithWinrates) =>
              s"$winrate% winrate: " + heroesWithWinrates.map(hero => s"${hero.heroName} ${hero.wins}-${hero.total - hero.wins}").mkString(", ")
            }
              .mkString("\n")
          )

        private def addPlayerName(resultsText: String, playerSpecificResults : Seq[Player]): UIO[String] =
          ZIO.succeed {
            val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
            s"""**$playerName**
               |$resultsText""".stripMargin
          }
      }


    )

  val dependencies: ZLayer[Any, Throwable, HeroRepoEnv with SttpClient with DotaApiRepo with LocalStorageRepo with DotaMatchRepoEnv] =
    HeroRepo.live ++ DotaMatchesRepo.dependencies

  def winLoss: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    ZIO.accessM(_.get.winLoss)

  def favoriteHeroes: ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.favoriteHeroes)

  def heroWinrates(heroStat: HeroStat, highestToLowest : Boolean) : ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.heroWinRates(heroStat, highestToLowest))

}
