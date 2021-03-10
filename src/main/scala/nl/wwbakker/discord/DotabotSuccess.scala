package nl.wwbakker.discord

sealed trait DotabotSuccess
case class SuccessText(message : String) extends DotabotSuccess
case class SuccessAttachment(data : Array[Byte]) extends DotabotSuccess