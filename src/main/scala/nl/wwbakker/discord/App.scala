package nl.wwbakker.discord

import nl.wwbakker.services.dota.statistics.StatisticsServices
import nl.wwbakker.services.dota.{Clients, DotaApiRepo, DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.generic.LocalStorageRepo
import zio.*

object App extends ZIOAppDefault{

    override def run: ZIO[Any & ZIOAppArgs & Scope, Any, Any] = {
      val program =
        for {
          ch <- ZIO.service[CommandHandler.Service]
          ds <- ZIO.service[DiscordJDA]
          _ <- ds
            .startServer
            .mapZIO(message =>
              for
                result <- ch.handleCommand(
                      args = removeMentions(message.content)
                    .split(" (?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").toIndexedSeq.map(stripQuotes),
                    commandPrefix = "@Wessel's Bot").either
                _ <- ds.sendMessage(result, message.channel)
              yield ()
            )
            .runDrain
        } yield ()


      val layer1 = Clients.live ++ LocalStorageRepo.ServiceImpl.live
      val layer2 = DotaApiRepo.ServiceImpl.live
      val layer3 = HeroRepo.ServiceImpl.live
      val layer4 = DotaMatchesRepo.ServiceImpl.live
      val layer5 = StatisticsServices.live
      val layer6 = CommandHandler.ServiceImpl.live
      val layer7 = DiscordJDA.live
      val layers = ZLayer.make[DiscordJDA & CommandHandler.Service](
        layer1, layer2, layer3, layer4, layer5, layer6, layer7
      )

      program.provide(layers)
    }


    private def stripStartQuote(s: String): String =
      if (s.startsWith("\""))
        s.substring(1)
      else
        s

    private def stripEndQuote(s: String): String =
      if (s.endsWith("\""))
        s.substring(0, s.length - 1)
      else
        s

    private def stripQuotes(s: String): String =
      stripEndQuote(stripStartQuote(s))

    private def removeMentions(content: String): String =
      content.replaceAll("<@[0-9]+> ", "")
}
