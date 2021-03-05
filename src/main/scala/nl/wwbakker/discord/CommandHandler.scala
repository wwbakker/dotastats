package nl.wwbakker.discord

import nl.wwbakker.dota.{DotaApiRepo, VisualizationService}
import nl.wwbakker.dota.DotaApiRepo.DotaApiRepo
import nl.wwbakker.dota.VisualizationService.NamedWinLoss
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio.console.Console
import zio.{ZIO, ZLayer}

object CommandHandler {

  val wesselId = 21842016
  val numberOfDaysInThePast = 14

  lazy val dependencies: ZLayer[Any, Any, SttpClient with DotaApiRepo with Console] =
    AsyncHttpClientZioBackend.layer() >+> DotaApiRepo.live ++ Console.live


  def printWinLoss: ZIO[DotaApiRepo with SttpClient, DotabotError, String] = (for {
    wessel <- DotaApiRepo.winLoss(wesselId).map(NamedWinLoss.from(_, "Wessel"))
    peers <- DotaApiRepo.peers(wesselId).map(_.map(NamedWinLoss.from))
    text <- VisualizationService.listWinLoss(peers.prepended(wessel))
  } yield "Results last 14 days:\n" + text)
    .mapError(TechnicalError.apply)

  def handleCommand(args: Seq[String], commandPrefix: String = ""): ZIO[DotaApiRepo with SttpClient, DotabotError, String] = {
    args.toList match {
      case "winloss" :: Nil =>
        printWinLoss
      case _ =>
        ZIO.fail(
          UserError(
            s"""Possible commands:
                $commandPrefix winloss
             """.stripMargin
          )
        )
    }
  }
}
