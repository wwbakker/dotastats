package nl.wwbakker.services.generic

import os.Path
import zio.{Task, ULayer, ZIO, ZLayer}

import java.nio.charset.Charset
import java.util.Base64

object LocalStorageRepo {
  private val matchesFilePath: Path = os.home / ".dotabot" / "matches"
  private val heroesFilePath: Path = os.home / ".dotabot" / "heroes"
  private val utf8: Charset = Charset.forName("UTF-8")

  //  Service description
  trait Service {
    def addMatch(m: String): Task[Unit]
    def listMatches(): Task[Seq[String]]

    def storeHeroes(heroes: String): Task[Unit]

    def heroes(): Task[Option[String]]
  }

  case class ServiceImpl() extends Service {

    override def addMatch(m: String): Task[Unit] = ZIO.attemptBlocking {
      os.write.append(
      matchesFilePath,
      data = (Base64.getEncoder.encodeToString(m.getBytes(utf8)) + "\n").getBytes(utf8),
      createFolders = true)
  }
    override def listMatches(): Task[Seq[String]] = ZIO.attemptBlocking {
      if (!os.exists(matchesFilePath))
        Nil
      else
        os.read(matchesFilePath)
          .split("\n").toSeq
          .map(Base64.getDecoder.decode)
          .map(new String(_, utf8))
    }

    override def storeHeroes(heroes: String): Task[Unit] = ZIO.attemptBlocking {
      os.write.over(
        heroesFilePath,
        data = heroes.getBytes(utf8),
        createFolders = true)
    }

    override def heroes(): Task[Option[String]] = ZIO.attemptBlocking {
      if (!os.exists(heroesFilePath))
        None
      else
        Some(os.read(heroesFilePath))
    }

  }

  object ServiceImpl {
    val live: ULayer[ServiceImpl] = ZLayer.succeed(ServiceImpl())
  }
}
