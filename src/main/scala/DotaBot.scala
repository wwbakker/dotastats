import sttp.client3._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

case class WinLoss(win: Int, lose: Int) {
  def rate : Double = (win.toDouble / (win.toDouble + lose.toDouble)) * 100d
}

case class NamedWinLoss(name: String, winLoss: WinLoss) {
  def text = s"$name: ${winLoss.win} wins, ${winLoss.lose} losses - ${Math.round(winLoss.rate)}% winrate"
}


case class Peer(account_id: Int, last_played: Int,
                win: Int, games: Int,
                with_win: Int, with_games: Int,
                against_win: Int, against_games: Int,
                with_gpm_sum: Int, with_xpm_sum: Int,
                personaname: Option[String], name: Option[String],
                is_contributor: Boolean, last_login: Option[String],
                avatar: Option[String], avatarfull: Option[String]) {

  def toNamedWinLoss : NamedWinLoss =
    NamedWinLoss(personaname.getOrElse(account_id.toString), WinLoss(win, games - win))
}


object DotaBot extends App {
  val wesselId = 21842016
  val numberOfDaysInThePast = 14


  def winLoss(playerId: Int, numberOfDaysInThePast: Int = 14) : Either[String, WinLoss] = {
    val request = basicRequest.get(uri"https://api.opendota.com/api/players/$playerId/wl?significant=0&date=$numberOfDaysInThePast")
    val backend = HttpURLConnectionBackend()
    val response = request.send(backend)
    response.body.flatMap(resp => decode[WinLoss](resp).swap.map(_.toString).swap)
  }

  def peers(playerId: Int, numberOfDaysInThePast: Int = 14) : Either[String, Seq[Peer]] = {
    val request = basicRequest.get(uri"https://api.opendota.com/api/players/$playerId/peers?significant=0&date=$numberOfDaysInThePast")
    val backend = HttpURLConnectionBackend()
    val response = request.send(backend)
    response.body.flatMap(resp => decode[Seq[Peer]](resp).swap.map(_.toString).swap)
  }

  val scores: Either[String, Seq[NamedWinLoss]] = for {
    wessel <- winLoss(wesselId).map(NamedWinLoss("Wessel",_))
    others <- peers(wesselId).map(_.map(_.toNamedWinLoss))
  } yield others.prepended(wessel)


  scores.swap.foreach(error => s"error: $error")

  val results = scores.map(a => a.map(_.text)).getOrElse(Seq("Error"))
  println("Results last 14 days:")
  results.foreach(println)
}
