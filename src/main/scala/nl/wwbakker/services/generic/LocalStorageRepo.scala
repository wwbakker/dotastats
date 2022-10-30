package nl.wwbakker.services.generic

import os.Path
import zio.{Task, ZIO, ZLayer}

import java.nio.charset.Charset
import java.util.Base64

object LocalStorageRepo {
  private val matchesFilePath: Path = os.home / ".dotabot" / "matches"
  private val utf8: Charset = Charset.forName("UTF-8")

  //  Service description
  type LocalStorageRepo = LocalStorageRepo.Service

  trait Service {
    def add(item: String): Task[Unit]

    def list(): Task[Seq[String]]
  }

  // Implementation
  val live: zio.Layer[Throwable, LocalStorageRepo] = ZLayer.succeed(
    new Service {
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

  )

  def add(item: String): ZIO[LocalStorageRepo, Throwable, Unit] =
    ZIO.environmentWithZIO(_.get.add(item))

  def list(): ZIO[LocalStorageRepo, Throwable, Seq[String]] =
    ZIO.environmentWithZIO(_.get.list())

}
