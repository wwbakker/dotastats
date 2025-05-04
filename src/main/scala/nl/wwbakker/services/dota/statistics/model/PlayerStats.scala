package nl.wwbakker.services.dota.statistics.model

import nl.wwbakker.services.dota.DotaMatchesRepo.Player

object PlayerStats {
  type PlayerStatGetter = Player => Float

  case class PlayerStatInfo(statGetter: PlayerStatGetter, friendlyName: String)

  private val statsMap = Map[String, PlayerStatInfo](
    "kills" -> PlayerStatInfo(_.kills.toFloat, "kills"),
    "deaths" -> PlayerStatInfo(_.deaths.toFloat, "deaths"),
    "assists" -> PlayerStatInfo(_.assists.toFloat, "assists"),
    "lastHits" -> PlayerStatInfo(_.last_hits.toFloat, "last hits"),
    "denies" -> PlayerStatInfo(_.denies.toFloat, "denies"),
    "gpm" -> PlayerStatInfo(p => p.total_gold.toFloat / (p.duration / 60f), "gold per minute"),
    "xpm" -> PlayerStatInfo(p => p.total_xp.toFloat / (p.duration / 60f), "experience per minute"),
    "duration" -> PlayerStatInfo(_.duration / 60f, "duration in minutes"),
  )

  def fromStatName(statName: String): Option[PlayerStatInfo] = statsMap.get(statName)

  val possibilities: Seq[String] = statsMap.keys.toSeq
}