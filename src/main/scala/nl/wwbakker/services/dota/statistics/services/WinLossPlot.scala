package nl.wwbakker.services.dota.statistics.services

import nl.wwbakker.services.dota.DotaMatchesRepo
import nl.wwbakker.services.dota.DotaMatchesRepo.Match
import nl.wwbakker.services.dota.statistics.model.Players
import zio.{ZIO, ZLayer}

object WinLossPlot {
  trait Service {
    def winLossPlot: ZIO[Any, Throwable, Array[Byte]]
  }


  case class ServiceImpl(dotaMatchesRepo: DotaMatchesRepo.Service) extends Service {

    override def winLossPlot: ZIO[Any, Throwable, Array[Byte]] = for {
      allGames <- dotaMatchesRepo.latestGames(Players.wesselId)
      pngPlot <- winLossPlot(allGames)
    } yield pngPlot

    private def winLossPlot(matches: Seq[Match]): ZIO[Any, Throwable, Array[Byte]] =
      ZIO.attempt {
        import de.sciss.chart.api._
        val dataSets = Players.everyone.map(winsLossesPlottedOfPlayerInTime(matches, _))
        val chart = XYLineChart(dataSets)
        chart.plot.getDomainAxis.setLabel("Days ago")
        chart.plot.getDomainAxis.setInverted(true)
        chart.plot.getRangeAxis.setLabel("Win surplus")
        chart.title = "Surplus wins per player"

        chart.encodeAsPNG()
      }

    private def winsLossesPlottedOfPlayerInTime(matches: Seq[Match], playerId: Int): (String, List[(Float, Int)]) = {
      val playerSpecificResults: Seq[DotaMatchesRepo.Player] =
        matches.flatMap(_.players.find(_.account_id.contains(playerId))).sortBy(_.start_time)
      val playerName = playerSpecificResults.headOption.flatMap(_.personaname).getOrElse("Unknown")
      val now = System.currentTimeMillis / 1000L // unix time format

      val (playerSpecificDataSet, _): (List[(Float, Int)], Int) = playerSpecificResults.foldLeft((List.empty[(Float, Int)], 0)) {
        case ((previousPoints, score), playerMatch) =>
          val newScore = score + playerMatch.win - playerMatch.lose
          val time = (now - playerMatch.start_time).toFloat / (60f * 60f * 24f) // convert seconds to days
          ((time, newScore) :: previousPoints, newScore)
      }

      (playerName, playerSpecificDataSet)
    }
  }

  object ServiceImpl {
    val live: ZLayer[DotaMatchesRepo.Service, Nothing, WinLossPlot.Service] = ZLayer {
      for {
        dmr <- ZIO.service[DotaMatchesRepo.Service]
      } yield ServiceImpl(dmr)
    }
  }
}