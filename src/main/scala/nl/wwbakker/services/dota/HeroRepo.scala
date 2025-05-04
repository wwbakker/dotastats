package nl.wwbakker.services.dota

import nl.wwbakker.services.generic.LocalStorageRepo
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.{IO, Task, ZIO, ZLayer}

object HeroRepo {

  case class Hero(id: Int, name: String, localized_name: String, primary_attr: String, attack_type: String,
                  roles: Seq[String], legs: Int)

  trait Service {
    def hero(id: Int): IO[Throwable, Hero]
    def heroes: IO[Throwable, Seq[Hero]]
  }

  case class ServiceImpl(localStorageRepo: LocalStorageRepo.Service, dotaApiRepo: DotaApiRepo.Service) extends Service {
    override def heroes: IO[Throwable, Seq[Hero]] =
      for {
        heroesJsonTextOption <- localStorageRepo.heroes()
        heroesJsonText <- putIfAbsent(heroesJsonTextOption)
        heroes <- decodeTo[Seq[Hero]](heroesJsonText)
      } yield heroes


    override def hero(id: Int): IO[Throwable, Hero] =
      for {
        allHeroes <- heroes
        hero <- findHero(id, allHeroes)
      } yield hero

    def putIfAbsent(heroesJsonTextOption: Option[String]): Task[String] = {
      heroesJsonTextOption match {
        case Some(cachedHeroes) => ZIO.succeed(cachedHeroes)
        case None => for {
          rawHeroes <- dotaApiRepo.rawHeroes
          _         <- localStorageRepo.storeHeroes(rawHeroes)
        } yield rawHeroes

      }
    }
    private implicit val decoderPlayer: JsonDecoder[Hero] = DeriveJsonDecoder.gen[Hero]

    private def decodeTo[A: JsonDecoder](body: String): IO[Throwable, A] =
      ZIO.fromEither(body.fromJson[A].swap.map(new IllegalStateException(_)).swap)

    private def findHero(id: Int, heroes: Seq[Hero]) =
      heroes.find(_.id == id) match {
        case Some(hero) => ZIO.succeed(hero)
        case None => ZIO.fail(new IllegalStateException(s"Hero with id $id not found."))
      }
  }

  object ServiceImpl {
    val live: ZLayer[DotaApiRepo.Service & LocalStorageRepo.Service, Nothing, HeroRepo.Service] =
      ZLayer {
        for {
          lsr <- ZIO.service[LocalStorageRepo.Service]
          dar <- ZIO.service[DotaApiRepo.Service]
        } yield ServiceImpl(lsr, dar)
      }
  }

}
