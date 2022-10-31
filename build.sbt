name := "dotabot"

version := "0.1"

scalaVersion := "2.13.5"

resolvers += Resolver.JCenterRepository // for ackcord
resolvers += "dv8tion" at "https://m2.dv8tion.net/releases"

libraryDependencies += "dev.zio" %% "zio" % "2.0.2"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.2"
libraryDependencies += "dev.zio" %% "zio-json" % "0.3.0"
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % "2.0.2" % Test,
  "dev.zio" %% "zio-test-sbt"      % "2.0.2" % Test,
  "dev.zio" %% "zio-test-magnolia" % "2.0.2" % Test
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % "3.8.3"
libraryDependencies += "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.8.3"

libraryDependencies += "net.katsstuff" %% "ackcord" % "0.18.1"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.4" // akka logging

libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.8.1"

libraryDependencies += "de.sciss" %% "scala-chart" % "0.8.0"
