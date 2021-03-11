package nl.wwbakker.discord

sealed trait DotabotSuccess
case class SuccessText(message : String) extends DotabotSuccess
case class SuccessPicture(data : Array[Byte]) extends DotabotSuccess