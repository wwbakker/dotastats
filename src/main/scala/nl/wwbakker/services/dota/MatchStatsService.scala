package nl.wwbakker.services.dota

import nl.wwbakker.services.dota.DotaApiRepo.DotaApiRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match, Player}
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.dota.MatchStatsService.HeroStats.HeroStatGetter
import nl.wwbakker.services.generic.LocalStorageRepo.LocalStorageRepo

import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{Has, Task, UIO, ULayer, URIO, ZIO, ZLayer}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

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

    def winLossPlot: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]]

    def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def heroWinRatesGroupedByPlayer(heroStat: Hero => String, highestToLowest : Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def heroWinRatesOverall(enemyTeam: Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String]

    def matchStatPlot(stat : PlayerStats.PlayerStatInfo): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]]
  }

  object HeroStats {
    type HeroStatGetter = Hero => String

    def name : HeroStatGetter = _.localized_name
    def primaryAttribute : HeroStatGetter = _.primary_attr
    def attackType : HeroStatGetter = _.attack_type
    def numberOfLegs : HeroStatGetter = _.legs.toString

    private val statsMap = Map[String, HeroStatGetter](
      "hero" -> name,
      "primaryAttribute" -> primaryAttribute,
      "attackType" -> attackType,
      "numberOfLegs" -> numberOfLegs
    )

    def fromStatName(statName : String) : Option[HeroStatGetter] = statsMap.get(statName)
    val possibilities: Seq[String] = statsMap.keys.toSeq
  }

  object PlayerStats {
    type PlayerStatGetter = Player => Float
    case class PlayerStatInfo(statGetter: PlayerStatGetter, friendlyName : String)

    private val statsMap = Map[String, PlayerStatInfo](
      "kills" -> PlayerStatInfo(_.kills, "kills"),
      "deaths" -> PlayerStatInfo(_.deaths, "deaths"),
      "assists" -> PlayerStatInfo(_.assists, "assists"),
      "lastHits" -> PlayerStatInfo(_.last_hits, "last hits"),
      "denies" -> PlayerStatInfo(_.denies, "denies"),
      "gpm" -> PlayerStatInfo(p => p.total_gold.toFloat / (p.duration / 60f), "gold per minute"),
      "xpm" -> PlayerStatInfo(p => p.total_xp.toFloat / (p.duration / 60f), "experience per minute"),
      "duration" -> PlayerStatInfo(_.duration / 60f, "duration in minutes"),
    )

    def fromStatName(statName : String) : Option[PlayerStatInfo] = statsMap.get(statName)
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


        private def winLossPlot(matches: Seq[Match]): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
          zio.Task {
            import de.sciss.chart.api._
            val dataSets = matchSubjects.map(winsLossesPlottedOfPlayerInTime(matches, _))
            val chart = XYLineChart(dataSets)
            chart.plot.getDomainAxis.setLabel("Days ago")
            chart.plot.getDomainAxis.setInverted(true)
            chart.plot.getRangeAxis.setLabel("Win surplus")
            chart.title = "Surplus wins per player"

            chart.encodeAsPNG()
          }

        override def winLossPlot: ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] = for {
          allGames <- DotaMatchesRepo.latestGames(wesselId)
          pngPlot  <- winLossPlot(allGames)
        } yield pngPlot



        private def winsLossesPlottedOfPlayerInTime(matches: Seq[Match], playerId: Int): (String, List[(Float, Int)]) = {
          val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
            matches.flatMap(_.players.find(_.account_id.contains(playerId))).sortBy(_.start_time)
          val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
          val now = System.currentTimeMillis / 1000L // unix time format

          val (playerSpecificDataSet, _): (List[(Float, Int)], Int) = playerSpecificResults.foldLeft((List.empty[(Float, Int)], 0)){
            case ((previousPoints, score), playerMatch) =>
              val newScore = score + playerMatch.win - playerMatch.lose
              val time = (now - playerMatch.start_time).toFloat / (60f * 60f * 24f) // convert seconds to days
              ((time, newScore) :: previousPoints, newScore)
          }

          (playerName, playerSpecificDataSet)
        }


        override def matchStatPlot(stat : PlayerStats.PlayerStatInfo): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] = for {
          allGames <- DotaMatchesRepo.latestGames(wesselId)
          pngPlot  <- matchStatPlot(allGames, stat)
        } yield pngPlot

        private def matchStatPlot(matches: Seq[Match], stat: PlayerStats.PlayerStatInfo): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
          zio.Task {
            import de.sciss.chart.api._
            val dataSets = matchSubjects.map(playerStatPlotOfPlayerInTime(matches, _, stat))

//            val ds: Seq[(String, Seq[Float])] = dataSets.map{case (name, seq) => (name, seq.map(_._2))}

            val chart = XYLineChart(dataSets)
            chart.plot.getDomainAxis.setLabel("Days ago")
            chart.plot.getDomainAxis.setInverted(true)
            chart.plot.getRangeAxis.setLabel(stat.friendlyName)
            chart.title = s"${stat.friendlyName.capitalize} per player"

            chart.encodeAsPNG()
          }

        private def playerStatPlotOfPlayerInTime(matches: Seq[Match], playerId: Int, stat: PlayerStats.PlayerStatInfo): (String, List[(Float, Float)]) = {
          val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
            matches.flatMap(_.players.find(_.account_id.contains(playerId))).sortBy(_.start_time)
          val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
          val now = System.currentTimeMillis / 1000L // unix time format

          val playerSpecificDataSet: List[(Float, Float)] = playerSpecificResults.foldLeft(List.empty[(Float, Float)]){
            case (previousPoints, playerMatch) =>
              val time = (now - playerMatch.start_time).toFloat / (60f * 60f * 24f) // convert seconds to days
              (time, stat.statGetter(playerMatch)) :: previousPoints
          }

          (playerName, playerSpecificDataSet)
        }


        override def favoriteHeroes: ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- ZIO.foreachPar(matchSubjects)(calculateFavoriteHeroes(allGames,allHeroes,_))
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

        override def heroWinRatesGroupedByPlayer(heroStat: HeroStatGetter, highestToLowest : Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- ZIO.foreachPar(matchSubjects)(heroWinrateForPlayer(allGames, allHeroes, _, heroStat, highestToLowest))
          } yield results.mkString("\n")

        private def heroWinrateForPlayer(matches : Seq[Match], heroes : Seq[Hero], playerId: Int, heroStat : HeroStatGetter, highToLow : Boolean) : ZIO[Any, Nothing, String] = for {
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

        private def heroStatWinsLosses(playerSpecificResults: Seq[Player], heroes : Seq[Hero], heroStatGetter : HeroStatGetter): UIO[Seq[HeroStatWinrate]] =
          ZIO.succeed {
            // for example: ("agi" -> Seq(heroId3, heroId6, heroId7))
            val heroesGroupedPerStatValue = playerSpecificResults.map(_.hero_id).groupBy(heroId => resolveHeroStat(heroes, heroId, heroStatGetter))
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

        private def groupAndOrderByWinrateBucket(heroWinrates: Seq[HeroStatWinrate]): UIO[Seq[(Long, Seq[HeroStatWinrate])]] =
          ZIO.succeed(heroWinrates.groupBy(hsw => Math.round(hsw.winrate / 10d) * 10).toSeq.sortBy(_._1))

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
              s"__$winrate% winrate__: " + heroesWithWinrates.map(hero => s"${hero.heroName} ${hero.wins}-${hero.total - hero.wins}").mkString(", ")
            }
              .mkString("\n")
          )

        private def addPlayerName(resultsText: String, playerSpecificResults : Seq[Player]): UIO[String] =
          ZIO.succeed {
            val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
            s"""**$playerName**
               |$resultsText""".stripMargin
          }

        override def heroWinRatesOverall(enemyTeam: Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
          for {
            allGames <- DotaMatchesRepo.latestGames(wesselId)
            allHeroes <- HeroRepo.heroes
            results <- heroWinrateOverall(allGames, allHeroes, wesselId, enemyTeam)
          } yield results

        private def heroWinrateOverall(matches : Seq[Match], heroes: Seq[Hero], playerIdOfUs : Int, enemyTeam: Boolean): ZIO[Any, Throwable, String] = {
          for {
            potpm <- playersOfTeamPerMatch(matches, playerIdOfUs, enemyTeam)
            heroWithWinrate : Seq[HeroStatWinrate]     <- ZIO.succeed(heroes.map{ hero =>
              val gamesPlayedWithHero = potpm.flatMap(_.players).filter(_.hero_id == hero.id)
              val numberOfGamesPlayedWithHero = gamesPlayedWithHero.length
              val numberOfGamesWonWithHero = gamesPlayedWithHero.count(_.win == 1)
              val winrate = percentage(numberOfGamesWonWithHero, numberOfGamesPlayedWithHero)
              HeroStatWinrate(hero.localized_name, numberOfGamesWonWithHero, numberOfGamesPlayedWithHero, winrate)
            }.filter(_.total > 0))
            gwr <- groupAndOrderByWinrateBucket(heroWithWinrate)
            resultsPerHero <- resultsText(gwr)
          } yield resultsPerHero

        }


        case class MatchWithSingleTeam(win : Boolean, players: Seq[Player])
        private def playersOfTeamPerMatch(matches : Seq[Match], playerIdInTeam : Int, enemyTeam : Boolean): Task[Seq[MatchWithSingleTeam]] = Task {
          matches.map{ dotaMatch =>
            val playerWon = dotaMatch.players.find(_.account_id.contains(playerIdInTeam)).map(_.win)
            playerWon match {
              case Some(winValueOfTeam) =>
                // Find who is in the same team, based on the fact that everyone in the same team either wins or loses.
                if (enemyTeam)
                  MatchWithSingleTeam(win = winValueOfTeam == 1, dotaMatch.players.filterNot(_.win == winValueOfTeam))
                else
                  MatchWithSingleTeam(win = winValueOfTeam == 1, dotaMatch.players.filter(_.win == winValueOfTeam))
              case None => throw new IllegalStateException(s"Could not find $playerIdInTeam")
            }
          }
        }
      }

    )

  val dependencies: ZLayer[Any, Throwable, HeroRepoEnv with SttpClient with DotaApiRepo with LocalStorageRepo with DotaMatchRepoEnv] =
    HeroRepo.live ++ DotaMatchesRepo.dependencies

  def winLoss: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, String] =
    ZIO.accessM(_.get.winLoss)

  def winLossPlot: ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
    ZIO.accessM(_.get.winLossPlot)

  def favoriteHeroes: ZIO[MatchStatsServiceEnv with HeroRepoEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.favoriteHeroes)

  def heroWinratesGroupedByPlayer(heroStat: HeroStatGetter, highestToLowest : Boolean) : ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.heroWinRatesGroupedByPlayer(heroStat, highestToLowest))

  def heroWinRatesOverall(enemyTeam: Boolean): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    ZIO.accessM(_.get.heroWinRatesOverall(enemyTeam))

  def matchStatPlot(stat : PlayerStats.PlayerStatInfo): ZIO[MatchStatsServiceEnv with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
    ZIO.accessM(_.get.matchStatPlot(stat))

}
