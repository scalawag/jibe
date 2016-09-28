import sbt.Keys._
import sbt._

object Dependencies {
  val sbtVersion = "0.13.11"

  val jsch = "com.jcraft" % "jsch" % "0.1.54"
  val commonsCodec = "commons-codec" % "commons-codec" % "1.10"
  val scalateCore = "org.scalatra.scalate" %% "scalate-core" % "1.7.0"
  val scalaGraphCore = "org.scala-graph" %% "graph-core" % "1.11.2"
  val scalaXml = "org.scala-lang" % "scala-xml" % "2.11.0-M4"
  val sprayJson = "io.spray" %% "spray-json" % "1.3.2"

  object timber {
    val version = "0.6.0"

    val backend = "org.scalawag.timber" %% "timber-backend" % version
    val slf4j = "org.scalawag.timber" %% "slf4j-over-timber" % version
  }

  val scalatest = "org.scalatest" %% "scalatest" % "3.0.0"
  val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.3.0"

}
