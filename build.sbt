name := "dotabot"

version := "0.1"

scalaVersion := "2.13.5"

resolvers += Resolver.JCenterRepository // for ackcord
resolvers += "dv8tion" at "https://m2.dv8tion.net/releases"

libraryDependencies += "dev.zio" %% "zio" % "1.0.4-2"
libraryDependencies += "dev.zio" %% "zio-streams" % "1.0.4-2"

libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % "3.1.7"
libraryDependencies += "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.1.7"

libraryDependencies += "net.katsstuff" %% "ackcord" % "0.18.1"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.4" // akka logging

libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.8.1"

libraryDependencies += "de.sciss" %% "scala-chart" % "0.8.0"

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)