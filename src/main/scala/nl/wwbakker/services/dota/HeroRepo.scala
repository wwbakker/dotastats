package nl.wwbakker.services.dota

import zio.{IO, Task, ULayer, ZIO, ZLayer}

import scala.io.Source
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode

object HeroRepo {

  case class Hero(id: Int, name: String, localized_name: String, primary_attr: String, attack_type: String,
                  roles: Seq[String], legs: Int)

  type HeroRepoEnv = HeroRepo.Service

  trait Service {
    def hero(id: Int): IO[Throwable, Hero]
    def heroes: IO[Throwable, Seq[Hero]]
  }

  val live: ULayer[HeroRepoEnv] =
    ZLayer.succeed(
      new Service {
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

        private def findHero(id : Int, heroes: Seq[Hero]) =
          heroes.find(_.id == id) match {
            case Some(hero) => ZIO.succeed(hero)
            case None => ZIO.fail(new IllegalStateException(s"Hero with id $id not found."))
          }

      }
    )

  // front-facing API
  def hero(id: Int): ZIO[HeroRepoEnv, Throwable, Hero] =
    ZIO.environmentWithZIO(_.get.hero(id))

  def heroes: ZIO[HeroRepoEnv, Throwable, Seq[Hero]] =
    ZIO.environmentWithZIO(_.get.heroes)

}
