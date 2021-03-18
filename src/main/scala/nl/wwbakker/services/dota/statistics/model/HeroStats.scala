package nl.wwbakker.services.dota.statistics.model

import nl.wwbakker.services.dota.HeroRepo.Hero

object HeroStats {
  type HeroStatGetter = Hero => String

  def name: HeroStatGetter = _.localized_name

  def primaryAttribute: HeroStatGetter = _.primary_attr

  def attackType: HeroStatGetter = _.attack_type

  def numberOfLegs: HeroStatGetter = _.legs.toString

  private val statsMap = Map[String, HeroStatGetter](
    "hero" -> name,
    "primaryAttribute" -> primaryAttribute,
    "attackType" -> attackType,
    "numberOfLegs" -> numberOfLegs
  )

  def fromStatName(statName: String): Option[HeroStatGetter] = statsMap.get(statName)

  val possibilities: Seq[String] = statsMap.keys.toSeq

  case class HeroStatWinrate(heroName: String, wins: Int, total: Int, winrate: Long)
}
