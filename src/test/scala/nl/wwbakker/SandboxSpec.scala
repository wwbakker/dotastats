package nl.wwbakker

import zio._
import zio.stream.ZStream
import zio.test._

import java.io.IOException

object SandboxSpec extends ZIOSpecDefault {
  def spec: Spec[Any, Nothing] = suite("HelloWorldSpec")(
    
    test("ZStream") {
      for {
        _  <- ZStream.fromIterable(Seq(1,2,3,4,5)).runForeach(a => Console.print(a).orDie)
        output <- TestConsole.output
      } yield assertTrue(output == Vector("1","2","3","4","5"))
    },
  )
}