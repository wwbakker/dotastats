package nl.wwbakker.discord

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import os.Path
import zio.*
import zio.stream.*

import java.util.Base64
import scala.util.Try
import scala.jdk.CollectionConverters.*

object DiscordJDA {
  case class Message(
    content: String,
    mentions: Seq[String]
  )


  // To generate the token, look in the developer portal under application > settings > bot > Build-A-Bot
  private val tokenPath: Path = os.home / ".dotabot" / "discord_token"
  private val token: String = os.read(tokenPath).strip()
  private val clientId: String = token.split('.').headOption.map(Base64.getDecoder.decode).map(new String(_)).get

  def startServer: ZStream[Any, Throwable, Message] =
    ZStream.asyncZIO { (emit: ZStream.Emit[Any, Throwable, Message, Unit]) =>
      ZIO.attemptBlocking {
        println("Starting JDA discord client")
        val jda = JDABuilder
          .createLight(token)
          .addEventListeners(new ListenerAdapter {
            override def onMessageReceived(event: MessageReceivedEvent): Unit = {
              val message = event.getMessage
              val mentionIds = message.getMentions.getUsers.asScala.map(_.getId)

              println(s"message: ${message.getContentRaw} - ${mentionIds.mkString(", ")}")
              if (mentionIds.contains(clientId)) {
                // Handle the command here
                println("Command received!")
                emit(ZIO.succeed(Chunk.single(Message(
                  content = message.getContentRaw,
                  mentions = mentionIds.toSeq
                ))))
              }
            }

            override def onException(event: ExceptionEvent): Unit =
              emit(ZIO.fail(Some(new Exception("JDA Exception occurred.", event.getCause))))

          })
          .build()

        jda.getRestPing.queue(ping => println("Logged in with ping: " + ping))

        jda.awaitReady()
      }.logError("Error while starting JDA discord client").forever
    }




}

//object EventListenerLogic extends ListenerAdapter:
//
//
//  override def onMessageReceived(event: MessageReceivedEvent) = {
//    val message = event.getMessage
//    val mentionIds = message.getMentions.getUsers.asScala.map(_.getId)
//
//    println(s"message: ${message.getContentRaw} - ${mentionIds.mkString(", ")}")
//    if (mentionIds.contains(clientId)) {
//      val result = commandHandler.handleCommand(
//        args = removeMentions(message.content)
//          .split(" (?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").toIndexedSeq.map(stripQuotes),
//        commandPrefix = "@Wessel's Bot")
//
//      result.either.flatMap(reply(message, _))
//    } else {
//      ZIO.unit
//    }
//  }
//
//
//  private def handleMessage(message: Message)(implicit cacheSnapshot: CacheSnapshot) = {
//    println(s"message: ${message.content} - ${message.mentions.mkString(", ")}")
//    if (message.mentions.contains(clientId)) {
//      val result = commandHandler.handleCommand(
//        args = removeMentions(message.content)
//          .split(" (?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").toIndexedSeq.map(stripQuotes),
//        commandPrefix = "@Wessel's Bot")
//
//      result.either.flatMap(reply(message, _))
//    } else {
//      ZIO.unit
//    }
//  }
//
//  private def reply(originalMessage: Message, result: Either[DotabotError, DotabotSuccess])(implicit c: CacheSnapshot): Task[Unit] = result match {
//    case Left(UserError(text)) => ZIO.fromFuture(implicit ec => send(CreateMessage(originalMessage.channelId, CreateMessageData(text))))
//    case Left(TechnicalError(e)) => ZIO.logError(s"Technical error. $e")
//    case Right(SuccessText(text)) => ZIO.fromFuture(implicit ec => send(CreateMessage(originalMessage.channelId, CreateMessageData(text.take(2000)))))
//    case Right(SuccessPicture(attachment)) => ZIO.fromFuture(implicit ec => send(CreateMessage(originalMessage.channelId, CreateMessageData(files = Seq(ByteFile(ContentType(MediaTypes.`image/png`), ByteString.fromArray(attachment), "plot.png"))))))
//  }
//
//  def stripStartQuote(s: String): String =
//    if (s.startsWith("\""))
//      s.substring(1)
//    else
//      s
//
//  def stripEndQuote(s: String): String =
//    if (s.endsWith("\""))
//      s.substring(0, s.length - 1)
//    else
//      s
//
//  def stripQuotes(s: String): String =
//    stripEndQuote(stripStartQuote(s))
//
//  def removeMentions(content: String): String =
//    content.replaceAll("<@[0-9]+> ", "")
//
//  private def send[A](request: Request[A])(implicit c: CacheSnapshot, ec: ExecutionContext): Future[Unit] =
//    discordClient.requestsHelper.run(request).value.map(_ => ())