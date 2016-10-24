import sbt._

object Dependencies {
  val sbtVersion = "0.13.11"

  val css = "com.github.japgolly.scalacss" %% "core" % "0.5.0"
  val jsch = "com.jcraft" % "jsch" % "0.1.54"
  val commonsCodec = "commons-codec" % "commons-codec" % "1.10"
  val scalateCore = "org.scalatra.scalate" %% "scalate-core" % "1.7.0"
  val scalaXml = "org.scala-lang" % "scala-xml" % "2.11.0-M4"
  val macroParadise = compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

  object graph {
    val core = "org.scala-graph" %% "graph-core" % "1.11.2"
    val dot = "com.assembla.scala-incubator" %% "graph-dot" % "1.11.0"
  }

  object akka {
    val version = "2.3.15"
    val actor = "com.typesafe.akka" %% "akka-actor" % version
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % version
  }

  object spray {
    val version = "1.3.3"

    val json = "io.spray" %% "spray-json" % "1.3.2" // WTF, spray?
    val can = "io.spray" %% "spray-can" % version
    val routing = "io.spray" %% "spray-routing" % version
  }

  object timber {
    val version = "0.6.0"

    val backend = "org.scalawag.timber" %% "timber-backend" % version
    val slf4j = "org.scalawag.timber" %% "slf4j-over-timber" % version
  }

  val scalatest = "org.scalatest" %% "scalatest" % "3.0.0"
  val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.3.0"
}
