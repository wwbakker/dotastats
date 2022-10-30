package nl.wwbakker.services.dota.statistics.implementations

import nl.wwbakker.misc.Utils.percentage
import nl.wwbakker.services.dota.Clients.SttpClient
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match, Player}
import nl.wwbakker.services.dota.{DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.dota.HeroRepo.{Hero, HeroRepoEnv}
import nl.wwbakker.services.dota.statistics.model.HeroStats.HeroStatWinrate
import nl.wwbakker.services.dota.statistics.model.Players
import zio.{Task, UIO, ZIO}

trait HeroWinratesOverallImpl extends HeroWinratesResultsTextImpl {
  def heroWinRatesOverall(enemyTeam: Boolean): ZIO[Any with DotaMatchRepoEnv with SttpClient with HeroRepoEnv, Throwable, String] =
    for {
      allGames <- DotaMatchesRepo.latestGames(Players.wesselId)
      allHeroes <- HeroRepo.heroes
      results <- heroWinrateOverall(allGames, allHeroes, Players.wesselId, enemyTeam)
    } yield results

  private def heroWinrateOverall(matches: Seq[Match], heroes: Seq[Hero], playerIdOfUs: Int, enemyTeam: Boolean): ZIO[Any, Throwable, String] = {
    for {
      potpm <- playersOfTeamPerMatch(matches, playerIdOfUs, enemyTeam)
      heroWithWinrate: Seq[HeroStatWinrate] = heroes.map { hero =>
        val gamesPlayedWithHero = potpm.flatMap(_.players).filter(_.hero_id == hero.id)
        val numberOfGamesPlayedWithHero = gamesPlayedWithHero.length
        val numberOfGamesWonWithHero = gamesPlayedWithHero.count(_.win == 1)
        val winrate = percentage(numberOfGamesWonWithHero, numberOfGamesPlayedWithHero)
        HeroStatWinrate(hero.localized_name, numberOfGamesWonWithHero, numberOfGamesPlayedWithHero, winrate)
      }.filter(_.total > 0)
      gwr <- groupAndOrderByWinrateBucket(heroWithWinrate)
      resultsPerHero <- resultsText(gwr)
    } yield resultsPerHero

  }

  private def groupAndOrderByWinrateBucket(heroWinrates: Seq[HeroStatWinrate]): UIO[Seq[(Long, Seq[HeroStatWinrate])]] =
    ZIO.succeed(heroWinrates.groupBy(hsw => Math.round(hsw.winrate / 10d) * 10).toSeq.sortBy(_._1))

  private case class MatchWithSingleTeam(win: Boolean, players: Seq[Player])

  private def playersOfTeamPerMatch(matches: Seq[Match], playerIdInTeam: Int, enemyTeam: Boolean): Task[Seq[MatchWithSingleTeam]] = ZIO.attempt {
    matches.map { dotaMatch =>
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
