package nl.wwbakker.discord

sealed trait DotabotError
case class UserError(s : String) extends DotabotError
case class TechnicalError(e : Throwable) extends DotabotError
