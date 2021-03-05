package nl.wwbakker.discord

import ackcord.data.UserId
import ackcord.requests.{CreateMessage, CreateMessageData, Request}
import ackcord.{APIMessage, CacheSnapshot, ClientSettings, DiscordClient}
import nl.wwbakker.dota.DotaApiRepo
import nl.wwbakker.dota.DotaApiRepo.DotaApiRepo
import os.Path
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio.{Exit, ZLayer}

import java.util.Base64
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DiscordBot extends App {
  val tokenPath: Path = os.home / ".dialogue" / "discord_token"
  val token: String = os.read(tokenPath)
  val clientId: UserId = UserId(token.split('.').headOption.map(Base64.getDecoder.decode).map(new String(_)).get)
  val clientSettings: ClientSettings = ClientSettings(token)
  lazy val zioDependencies: ZLayer[Any, DotabotError, SttpClient with DotaApiRepo] =
    AsyncHttpClientZioBackend.layer().mapError(TechnicalError.apply) >+> DotaApiRepo.live.mapError(TechnicalError.apply)

  import clientSettings.executionContext

  val client: DiscordClient = Await.result(clientSettings.createClient(), Duration.Inf)

  def start(): Unit = {
    println(s"clientid: $clientId")

    def stripStartQuote(s: String): String =
      if (s.startsWith("\""))
        s.substring(1)
      else
        s

    def stripEndQuote(s: String): String =
      if (s.endsWith("\""))
        s.substring(0, s.length - 1)
      else
        s

    def stripQuotes(s: String): String =
      stripEndQuote(stripStartQuote(s))

    def removeMentions(content: String): String =
      content.replaceAll("<@![0-9]+> ", "")

    lazy val registration = client.onEventSideEffects { implicit c => {
      case APIMessage.MessageCreate(_, message, _) if message.content.contains("stop bot") =>
        println("stopping")
        stop()
        client.logout()
        client.shutdownAckCord()
      case APIMessage.MessageCreate(_, message, _) =>
        println(s"message: ${message.content} - ${message.mentions.mkString(", ")}")
        if (message.mentions.contains(clientId)) {
          val result = CommandHandler.handleCommand(
            args = removeMentions(message.content)
              .split(" (?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").toIndexedSeq.map(stripQuotes),
            commandPrefix = "@Wessel's Bot")

          zio.Runtime.default.unsafeRunSync(result.provideLayer(zioDependencies)) match {
            case Exit.Success(text) => send(CreateMessage(message.channelId, CreateMessageData(text)))
            case Exit.Failure(cause) => cause.failureOption match {
              case Some(TechnicalError(e)) => println(s"Technical error. $e")
              case Some(UserError(text)) => send(CreateMessage(message.channelId, CreateMessageData(text)))
              case None => println("Unknown error")
            }
          }
        }
    }
    }

    def send[A](request: Request[A])(implicit c: CacheSnapshot): Unit =
      client.requestsHelper.run(request).map(_ => ())

    def stop(): Unit = registration.stop()

    registration
    client.login()
  }

  start()
}
