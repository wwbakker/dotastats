package nl.wwbakker.discord

import nl.wwbakker.services.dota.Clients.SttpClient
import nl.wwbakker.services.dota.statistics.StatisticsServices
import nl.wwbakker.services.dota.statistics.services.FavoriteHero
import nl.wwbakker.services.dota.{Clients, DotaApiRepo, DotaMatchesRepo, HeroRepo}
import nl.wwbakker.services.generic.LocalStorageRepo
import zio.stream.ZSink
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

object App extends ZIOAppDefault{


//  val zioDependencies = Discord.ServiceImpl.live ++ Clients.live ++ StatisticsServices.live ++ LocalStorageRepo.ServiceImpl.live ++ DotaApiRepo.ServiceImpl.live ++ DotaMatchesRepo.ServiceImpl.live ++ CommandHandler.ServiceImpl.live



  val handleMessages: ZIO[Discord.Service, Throwable, Unit] =
    ZIO.service[Discord.Service].flatMap { service =>
      service.handleMessages.runDrain
    }


//  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
//    handleMessages.provideLayer(zioDependencies)

    override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
      val services = for {
        lsr <- ZIO.service[LocalStorageRepo.Service]
        c <- ZIO.service[Clients.SttpClient]
        dar <- ZIO.service[DotaApiRepo.Service]
        ds <- ZIO.service[Discord.Service]
      } yield (ds.handleMessages.runDrain)




//      val s = Clients.live >>> DotaApiRepo.ServiceImpl.live
      val layer1 = Clients.live ++ LocalStorageRepo.ServiceImpl.live ++ HeroRepo.ServiceImpl.live
      val layer2 = DotaApiRepo.ServiceImpl.live
      val layer3 = DotaMatchesRepo.ServiceImpl.live
      val layer4 = StatisticsServices.live
      val layer5 = CommandHandler.ServiceImpl.live
      val layer6 = Discord.ServiceImpl.liveDiscordClient
      val layer7 = Discord.ServiceImpl.live
//      val layer12 = layer1 >+> layer2

      services.provide(layer1 >+> layer2 >+> layer3 >+> layer4 >+> layer5 >+> layer6 >+> layer7)
    }
}
