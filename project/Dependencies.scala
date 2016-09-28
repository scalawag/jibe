import sbt.Keys._
import sbt._

object Dependencies {
  val sbtVersion = "0.13.11"

  lazy val jsch = "com.jcraft" % "jsch" % "0.1.54"
  lazy val commonsCodec = "commons-codec" % "commons-codec" % "1.10"
  lazy val scalateCore = "org.scalatra.scalate" %% "scalate-core" % "1.7.0"
  lazy val scalaGraphCore = "org.scala-graph" %% "graph-core" % "1.11.2"
  lazy val scalaXml = "org.scala-lang" % "scala-xml" % "2.11.0-M4"
  lazy val sprayJson = "io.spray" %% "spray-json" % "1.3.2"

  object timber {
    lazy val backend = "org.scalawag.timber" %% "timber-backend" % "0.6.0"
    lazy val slf4j = "org.scalawag.timber" %% "slf4j-over-timber" % "0.6.0"
  }

  lazy val scalatest = "org.scalatest" %% "scalatest" % "3.0.0"
  lazy val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.3.0"

}
