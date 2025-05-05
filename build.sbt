name := "dotabot"

version := "0.1"

scalaVersion := "3.6.4"

Global / onChangedBuildSource := ReloadOnSourceChanges

scalacOptions += "-deprecation"

resolvers += Resolver.JCenterRepository // for ackcord
resolvers += "dv8tion" at "https://m2.dv8tion.net/releases"

libraryDependencies += "dev.zio" %% "zio" % "2.1.17"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.1.17"
libraryDependencies += "dev.zio" %% "zio-json" % "0.7.42"
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % "2.1.17" % Test,
  "dev.zio" %% "zio-test-sbt"      % "2.1.17" % Test,
  "dev.zio" %% "zio-test-magnolia" % "2.1.17" % Test
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.3"
libraryDependencies += "com.softwaremill.sttp.client4" %% "zio" % "4.0.3"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18" // akka logging

libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.11.4"

libraryDependencies += "de.sciss" %% "scala-chart" % "0.8.0"

libraryDependencies += "net.dv8tion" % "JDA" % "5.5.1"
