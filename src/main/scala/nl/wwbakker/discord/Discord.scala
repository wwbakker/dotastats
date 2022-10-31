package nl.wwbakker.discord

import ackcord.data.{Message, UserId}
import ackcord.requests.CreateMessageFile.ByteFile
import ackcord.requests.{CreateMessage, CreateMessageData, Request}
import ackcord.{APIMessage, CacheSnapshot, ClientSettings, DiscordClient}
import akka.http.scaladsl.model.{ContentType, MediaTypes}
import akka.util.ByteString
import os.Path
import zio.stream.ZStream
import zio.{Scope, Task, UIO, URIO, ZIO, ZLayer}

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

object Discord {

  trait Service {
    def handleMessages: ZIO[Scope, Nothing, ZStream[Any, Throwable, Unit]]
  }

  case class ServiceImpl(discordClient: DiscordClient, clientId: UserId, commandHandler: CommandHandler.Service) extends Service {
    case class MessageAndCacheSnapshot(message: Message, cacheSnapshot: CacheSnapshot)

    override def handleMessages: ZIO[Scope, Nothing, ZStream[Any, Throwable, Unit]] =
      messages.map(_.mapZIO(mc => handleMessage(mc.message)(mc.cacheSnapshot)))

    private def messagesStream(ref: zio.Ref[Option[ListenStopper]]): ZStream[Any, Throwable, MessageAndCacheSnapshot] =
      ZStream.async[Any, Throwable, MessageAndCacheSnapshot] { callback =>
        println("Registering messages stream.")
        val registration = discordClient.onEventSideEffects { implicit c => {
          case APIMessage.MessageCreate(_, message, _, _) => callback(ZIO.succeed(zio.Chunk(MessageAndCacheSnapshot(message, c))))
        }
        }
        ref.set(Some(ListenStopper{ () =>
          println("Deregistering message stream.")
          registration.stop()
        }))
      }

    case class ListenStopper(stop : () => Unit)
    private val messages: ZIO[Scope, Nothing, ZStream[Any, Throwable, MessageAndCacheSnapshot]] = {
        for {
          ref <- zio.Ref.make[Option[ListenStopper]](None)
          messagesStream <- ZIO.acquireRelease(ZIO.succeed(messagesStream(ref)))(_ =>
            ref.get.map(listenerStopperOption =>
              listenerStopperOption.foreach(_.stop())
            )
          )
        } yield messagesStream
      }

    private def handleMessage(message: Message)(implicit cacheSnapshot: CacheSnapshot) = {
      println(s"message: ${message.content} - ${message.mentions.mkString(", ")}")
      if (message.mentions.contains(clientId)) {
        val result = commandHandler.handleCommand(
          args = removeMentions(message.content)
            .split(" (?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").toIndexedSeq.map(stripQuotes),
          commandPrefix = "@Wessel's Bot")

        result.either.flatMap(reply(message, _))
      } else {
        ZIO.unit
      }
    }

    private def reply(originalMessage: Message, result: Either[DotabotError, DotabotSuccess])(implicit c: CacheSnapshot) : Task[Unit] = result match {
      case Left(UserError(text)) => ZIO.fromFuture(implicit ec => send(CreateMessage(originalMessage.channelId, CreateMessageData(text))))
      case Left(TechnicalError(e)) => ZIO.logError(s"Technical error. $e")
      case Right(SuccessText(text)) => ZIO.fromFuture(implicit ec => send(CreateMessage(originalMessage.channelId, CreateMessageData(text.take(2000)))))
      case Right(SuccessPicture(attachment)) => ZIO.fromFuture(implicit ec => send(CreateMessage(originalMessage.channelId, CreateMessageData(files = Seq(ByteFile(ContentType(MediaTypes.`image/png`), ByteString.fromArray(attachment), "plot.png"))))))
    }

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
      content.replaceAll("<@[0-9]+> ", "")

    private def send[A](request: Request[A])(implicit c: CacheSnapshot, ec: ExecutionContext): Future[Unit] =
      discordClient.requestsHelper.run(request).value.map(_ => ())

  }

  object ServiceImpl {
    // To generate the token, look in the developer portal under application > settings > bot > Build-A-Bot
    private val tokenPath: Path = os.home / ".dotabot" / "discord_token"
    private val token: String = os.read(tokenPath).strip()
    private val clientId: UserId = UserId(token.split('.').headOption.map(Base64.getDecoder.decode).map(new String(_)).get)
    private val clientSettings: ClientSettings = ClientSettings(token)
    private val acquire: Task[DiscordClient] = ZIO.fromFuture { implicit ec =>
      for {
        _ <- Future.successful(println("Creating Discord client"))
        client <- clientSettings.createClient()
        _ = client.login()
      } yield client
    }
    private def release(client: DiscordClient): URIO[Any, Unit] = ZIO.fromFuture { implicit ec =>
      for {
        _ <- Future.successful(println("Shutting down discord client"))
        _ <- client.shutdownAckCord().recover(e => println(s"Could not shut down ackcord: $e. Proceeding"))
      } yield ()
    }.orDie

    val liveDiscordClient: ZLayer[Any, Throwable, DiscordClient] =
      ZLayer.scoped(ZIO.acquireRelease(acquire)(release))

    val live: ZLayer[DiscordClient with CommandHandler.Service, Nothing, Discord.Service] = ZLayer {
      for {
        commandHandler <- ZIO.service[CommandHandler.Service]
        discordClient <- ZIO.service[DiscordClient]
      } yield ServiceImpl(discordClient, clientId, commandHandler)
    }
  }

}
