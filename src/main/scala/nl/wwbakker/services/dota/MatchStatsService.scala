package nl.wwbakker.services.dota

import nl.wwbakker.services.dota.DotaApiRepo.DotaApiRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match}
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.generic.LocalStorageRepo.LocalStorageRepo
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{Has, UIO, ULayer, ZIO, ZLayer}

object MatchStatsService {
  private val wesselId = 21842016
  private val nikolaId = 39067906
  private val chairmanId = 55277122
  private val beamcannon = 56624400
  private val buzzKillington = 66268281

  private val matchSubjects = Seq(wesselId, chairmanId, beamcannon, buzzKillington, nikolaId)

  // API

  type MatchStatsServiceEnv = Has[MatchStatsService.Service]

  trait Service {
    def winLoss: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, String]

    def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def highestWinRateHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def lowestWinRateHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]
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

        override def highestWinRateHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- ZIO.foreachPar(matchSubjects)(calculateHighestWinrateHeroes(allGames,allHeroes,_))
          } yield results.mkString("\n")

        override def lowestWinRateHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- ZIO.foreachPar(matchSubjects)(calculateLowestWinrateHeroes(allGames,allHeroes,_))
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
            s"**${playerName}**: ${wins} wins, ${losses} losses - ${winrate}% winrate"
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

        private def calculateHighestWinrateHeroes(matches : Seq[Match], heroes : Seq[Hero], playerId: Int) : UIO[String] =
          ZIO.succeed {
            val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
              matches.flatMap(_.players.find(_.account_id.contains(playerId)))
            val playedHeroes = playerSpecificResults.map(_.hero_id).distinct
            val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")

            val results = playedHeroes
              .map{ heroId =>
                val numberOfTimesPlayed = playerSpecificResults.count(_.hero_id == heroId)
                val numberOfTimesWon = playerSpecificResults.filter(_.win == 1).count(_.hero_id == heroId)
                val winrate = percentage(numberOfTimesWon, numberOfTimesPlayed)
                val heroName = heroes.find(_.id == heroId).map(_.localized_name).getOrElse("Unknown hero")
                (heroName, numberOfTimesWon, numberOfTimesPlayed, winrate)
              }
              .sortBy(_._4.toDouble)
              .reverse
              .take(5)
              .map{ case (heroName, numberOfTimesWon, numberOfTimesPlayed, winrate) =>
                s"$heroName: ${numberOfTimesWon} wins, ${numberOfTimesPlayed - numberOfTimesWon} losses - ${winrate}% winrate)"
              }
              .mkString("\n")

            s"""**$playerName**:
               |$results""".stripMargin
          }

        private def calculateLowestWinrateHeroes(matches : Seq[Match], heroes : Seq[Hero], playerId: Int) : UIO[String] =
          ZIO.succeed {
            val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
              matches.flatMap(_.players.find(_.account_id.contains(playerId)))
            val playedHeroes = playerSpecificResults.map(_.hero_id).distinct
            val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")

            val results = playedHeroes
              .map{ heroId =>
                val numberOfTimesPlayed = playerSpecificResults.count(_.hero_id == heroId)
                val numberOfTimesWon = playerSpecificResults.filter(_.win == 1).count(_.hero_id == heroId)
                val winrate = percentage(numberOfTimesWon, numberOfTimesPlayed)
                val heroName = heroes.find(_.id == heroId).map(_.localized_name).getOrElse("Unknown hero")
                (heroName, numberOfTimesWon, numberOfTimesPlayed, winrate)
              }
              .sortBy(_._4.toDouble)
              .take(5)
              .map{ case (heroName, numberOfTimesWon, numberOfTimesPlayed, winrate) =>
                s"$heroName: ${numberOfTimesWon} wins, ${numberOfTimesPlayed - numberOfTimesWon} losses - ${winrate}% winrate)"
              }
              .mkString("\n")

            s"""**$playerName**:
               |$results""".stripMargin
          }

        private def percentage(part: Int, total : Int) : String =
          Math.round((part.toDouble / total.toDouble) * 100d).toString
      }


    )


  val dependencies: ZLayer[Any, Throwable, HeroRepoEnv with SttpClient with DotaApiRepo with LocalStorageRepo with DotaMatchRepoEnv] =
    HeroRepo.live ++ DotaMatchesRepo.dependencies

  def winLoss: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    ZIO.accessM(_.get.winLoss)

  def favoriteHeroes: ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.favoriteHeroes)

  def highestWinrateHeroes: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.highestWinRateHeroes)

  def lowestWinrateHeroes: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.lowestWinRateHeroes)
}
