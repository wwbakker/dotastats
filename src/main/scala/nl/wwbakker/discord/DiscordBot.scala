package nl.wwbakker.discord

import ackcord.data.UserId
import ackcord.requests.CreateMessageFile.ByteFile
import ackcord.requests.{CreateMessage, CreateMessageData, Request}
import ackcord.{APIMessage, CacheSnapshot, ClientSettings, DiscordClient}
import akka.http.scaladsl.model.{ContentType, MediaTypes}
import akka.util.ByteString
import nl.wwbakker.services.dota.statistics.StatisticsServices
import nl.wwbakker.services.dota.{Clients, DotaApiRepo, DotaMatchesRepo}
import nl.wwbakker.services.generic.LocalStorageRepo
import os.Path
import zio.{Exit, Unsafe}

import java.util.Base64
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DiscordBot extends App {
  // To generate the token, look in the developer portal under application > settings > bot > Build-A-Bot
  val tokenPath: Path = os.home / ".dotabot" / "discord_token"
  val token: String = os.read(tokenPath).strip()
  val clientId: UserId = UserId(token.split('.').headOption.map(Base64.getDecoder.decode).map(new String(_)).get)
  val clientSettings: ClientSettings = ClientSettings(token)
  val zioDependencies =
    Clients.live ++ StatisticsServices.live ++ LocalStorageRepo.ServiceImpl.live ++ DotaApiRepo.ServiceImpl.live ++ DotaMatchesRepo.ServiceImpl.live

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
      case APIMessage.MessageCreate(_, message, _, _) if message.content.contains("stop bot") =>
        println("stopping")
        stop()
        client.logout()
        client.shutdownAckCord()
      case APIMessage.MessageCreate(_, message, _, _) =>
        println(s"message: ${message.content} - ${message.mentions.mkString(", ")}")
        if (message.mentions.contains(clientId)) {
          val result = CommandHandler.handleCommand(
            args = removeMentions(message.content)
              .split(" (?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").toIndexedSeq.map(stripQuotes),
            commandPrefix = "@Wessel's Bot")

          Unsafe.unsafe{ implicit unsafe =>
            zio.Runtime.default.unsafe.run(result.provideLayer(zioDependencies)) match {
              case Exit.Success(SuccessText(text)) => send(CreateMessage(message.channelId, CreateMessageData(text.take(2000))))
              case Exit.Success(SuccessPicture(attachment)) => send(CreateMessage(message.channelId, CreateMessageData(files = Seq(ByteFile(ContentType(MediaTypes.`image/png`), ByteString.fromArray(attachment), "plot.png")))))
              case Exit.Failure(cause) => cause.failureOption match {
                case Some(TechnicalError(e)) => println(s"Technical error. $e")
                case Some(UserError(text)) => send(CreateMessage(message.channelId, CreateMessageData(text)))
                case None => println("Unknown error")
              }
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
