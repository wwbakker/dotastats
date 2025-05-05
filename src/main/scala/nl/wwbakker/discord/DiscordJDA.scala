package nl.wwbakker.discord

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.FileUpload
import os.Path
import zio.*
import zio.stream.*

import java.util.Base64
import scala.jdk.CollectionConverters.*

class DiscordJDA() {
  opaque type Channel = MessageChannelUnion

  case class Message(
    content: String,
    mentions: Seq[String],
    channel: Channel
  )

  // To generate the token, look in the developer portal under application > settings > bot > Build-A-Bot
  private val tokenPath: Path = os.home / ".dotabot" / "discord_token"
  private val token: String = os.read(tokenPath).strip()
  private val clientId: String = token.split('.').headOption.map(Base64.getDecoder.decode).map(new String(_)).get

  def sendMessage(dotabotResult: Either[DotabotError, DotabotSuccess], channel: Channel): Task[Unit] = {
      dotabotResult match {
        case Right(SuccessText(text)) => ZIO.attemptBlocking(channel.sendMessage(text).queue())
        case Right(SuccessPicture(data)) => ZIO.attemptBlocking(channel.sendMessage("").addFiles(FileUpload.fromData(data, "image.jpg")).queue())
        case Left(UserError(text)) => ZIO.attemptBlocking(channel.sendMessage(text).queue())
        case Left(TechnicalError(e)) => ZIO.logErrorCause("Technische fout" , Cause.fail(e))
      }
  }

  def startServer: ZStream[Any, Throwable, Message] =
    ZStream.asyncZIO { (emit: ZStream.Emit[Any, Throwable, Message, Unit]) =>
      ZIO.attemptBlocking {
        println("Starting JDA discord client")
        val intents = GatewayIntent.getIntents(GatewayIntent.DEFAULT)
        intents.add(GatewayIntent.MESSAGE_CONTENT)
        val jda = JDABuilder
          .createLight(token, intents)
          .addEventListeners(new ListenerAdapter {
            override def onMessageReceived(event: MessageReceivedEvent): Unit = {
              val message = event.getMessage
              val messageContents = message.getContentRaw
              val mentionIds = message.getMentions.getUsers.asScala.map(_.getId)
              println(s"message: ${messageContents} - ${mentionIds.mkString(", ")}")
              if (mentionIds.contains(clientId)) {
                // Handle the command here
                println("Command received!")
                emit(ZIO.succeed(Chunk.single(Message(
                  content = messageContents,
                  mentions = mentionIds.toSeq,
                  channel = event.getChannel
                ))))
              }
            }

            override def onException(event: ExceptionEvent): Unit =
              emit(ZIO.fail(Some(new Exception("JDA Exception occurred.", event.getCause))))

          })
          .build()

        jda.getRestPing.queue(ping => println("Logged in with ping: " + ping))

        jda.awaitReady()
      }.logError("Error while starting JDA discord client")
    }

}

object DiscordJDA:
  def live: ZLayer[Any, Nothing, DiscordJDA] =
    ZLayer(
      ZIO.succeed(DiscordJDA())

    )