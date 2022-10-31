package nl.wwbakker.discord

import nl.wwbakker.services.dota.statistics.StatisticsServices
import nl.wwbakker.services.dota.{Clients, DotaApiRepo, DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.generic.LocalStorageRepo
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

object App extends ZIOAppDefault{

    override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
      val program = for {
        ds <- ZIO.service[Discord.Service]
        _ <- ds.handleMessages.runDrain
      } yield ()

      val layer1 = Clients.live ++ LocalStorageRepo.ServiceImpl.live
      val layer2 = DotaApiRepo.ServiceImpl.live
      val layer3 = HeroRepo.ServiceImpl.live
      val layer4 = DotaMatchesRepo.ServiceImpl.live
      val layer5 = StatisticsServices.live
      val layer6 = CommandHandler.ServiceImpl.live
      val layer7 = Discord.ServiceImpl.liveDiscordClient
      val layer8 = Discord.ServiceImpl.live

      program.provide(layer1 >+> layer2 >+> layer3 >+> layer4 >+> layer5 >+> layer6 >+> layer7 >+> layer8)
    }
}
