package nl.wwbakker.services.dota

import nl.wwbakker.services.dota.DotaApiRepo.{Peer, WinLoss}
import zio.{UIO, ZIO}

object VisualizationService {

  case class NamedWinLoss(name: String, winLoss: WinLoss) {
    def text = s"$name: ${winLoss.win} wins, ${winLoss.lose} losses - ${Math.round(winLoss.rate)}% winrate"
  }

  object NamedWinLoss {
    def from(peer: Peer): NamedWinLoss =
      NamedWinLoss(peer.personaname.getOrElse(peer.account_id.toString), WinLoss(peer.win, peer.games - peer.win))

    def from(winLoss: WinLoss, name: String): NamedWinLoss =
      NamedWinLoss(name, winLoss)
  }

  def listWinLoss(namedWinLosses: Seq[NamedWinLoss]): UIO[String] =
    ZIO.succeed(namedWinLosses.map(_.text).mkString(System.lineSeparator()))

}
