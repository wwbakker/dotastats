package nl.wwbakker.services.dota

import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import zio.{IO, ZIO, ZLayer}

import scala.io.Source

object HeroRepo {

  case class Hero(id: Int, name: String, localized_name: String, primary_attr: String, attack_type: String,
                  roles: Seq[String], legs: Int)

  trait Service {
    def hero(id: Int): IO[Throwable, Hero]
    def heroes: IO[Throwable, Seq[Hero]]
  }

  case class ServiceImpl() extends Service {
    override def heroes: IO[Throwable, Seq[Hero]] =
      for {
        heroesJsonText <- ZIO.attempt {
          Source.fromResource("heroes.json").mkString
        }
        heroes <- decodeTo[Seq[Hero]](heroesJsonText)
      } yield heroes


    override def hero(id: Int): IO[Throwable, Hero] =
      for {
        allHeroes <- heroes
        hero <- findHero(id, allHeroes)
      } yield hero


    private def decodeTo[A: Decoder](body: String): IO[Throwable, A] =
      ZIO.fromEither(decode[A](body).swap.map(new IllegalStateException(_)).swap)

    private def findHero(id: Int, heroes: Seq[Hero]) =
      heroes.find(_.id == id) match {
        case Some(hero) => ZIO.succeed(hero)
        case None => ZIO.fail(new IllegalStateException(s"Hero with id $id not found."))
      }
  }

  object ServiceImpl {
    val live : ZLayer[Any, Nothing, HeroRepo.Service] =
      ZLayer.succeed(new ServiceImpl())
  }

  // front-facing API
  def hero(id: Int): ZIO[HeroRepo.Service, Throwable, Hero] =
    ZIO.environmentWithZIO(_.get.hero(id))

  def heroes: ZIO[HeroRepo.Service, Throwable, Seq[Hero]] =
    ZIO.environmentWithZIO(_.get.heroes)

}
