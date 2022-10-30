package nl.wwbakker.services.generic

import os.Path
import zio.{Task, ULayer, ZIO, ZLayer}

import java.nio.charset.Charset
import java.util.Base64

object LocalStorageRepo {
  private val matchesFilePath: Path = os.home / ".dotabot" / "matches"
  private val utf8: Charset = Charset.forName("UTF-8")

  //  Service description
  trait Service {
    def add(item: String): Task[Unit]

    def list(): Task[Seq[String]]
  }

  case class ServiceImpl() extends Service {
    override def add(item: String): Task[Unit] = ZIO.attempt {
      os.write.append(
        matchesFilePath,
        data = (Base64.getEncoder.encodeToString(item.getBytes(utf8)) + "\n").getBytes(utf8),
        createFolders = true)
    }

    override def list(): Task[Seq[String]] = ZIO.attempt {
      if (!os.exists(matchesFilePath))
        Nil
      else
        os.read(matchesFilePath)
          .split("\n").toSeq
          .map(Base64.getDecoder.decode)
          .map(new String(_, utf8))
    }
  }

  object ServiceImpl {
    val live: ULayer[ServiceImpl] = ZLayer.succeed(ServiceImpl())
  }

  def add(item: String): ZIO[LocalStorageRepo.Service, Throwable, Unit] =
    ZIO.environmentWithZIO(_.get.add(item))

  def list(): ZIO[LocalStorageRepo.Service, Throwable, Seq[String]] =
    ZIO.environmentWithZIO(_.get.list())

}
