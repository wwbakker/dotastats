package nl.wwbakker.services.dota.statistics.implementations

import nl.wwbakker.services.dota.Clients.SttpClient
import nl.wwbakker.services.dota.DotaMatchesRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.{DotaMatchRepoEnv, Match}
import nl.wwbakker.services.dota.statistics.model.{PlayerStats, Players}
import zio.ZIO

trait MatchStatPlotImpl {

  def matchStatPlot(stat: PlayerStats.PlayerStatInfo): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] = for {
    allGames <- DotaMatchesRepo.latestGames(Players.wesselId)
    pngPlot <- matchStatPlot(allGames, stat)
  } yield pngPlot

  private def matchStatPlot(matches: Seq[Match], stat: PlayerStats.PlayerStatInfo): ZIO[Any with DotaMatchRepoEnv with SttpClient, Throwable, Array[Byte]] =
    ZIO.attempt {
      import de.sciss.chart.api._
      val dataSets = Players.everyone.map(playerStatPlotOfPlayerInTime(matches, _, stat))

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

    val playerSpecificDataSet: List[(Float, Float)] = playerSpecificResults.foldLeft(List.empty[(Float, Float)]) {
      case (previousPoints, playerMatch) =>
        val time = (now - playerMatch.start_time).toFloat / (60f * 60f * 24f) // convert seconds to days
        (time, stat.statGetter(playerMatch)) :: previousPoints
    }

    (playerName, playerSpecificDataSet)
  }
}
