import sbt._

import Dependencies._

import Utils._

val commonSettings = Seq(
  name := "jibe",
  version := "1.0.0-SNAPSHOT",
  organization := "org.scalawag",
  scalaVersion := "2.11.8",
  parallelExecution in IntegrationTest := false,
  resolvers ++= Seq (
    "JAnalyse Repository" at "http://www.janalyse.fr/repository/",
    Resolver.sonatypeRepo("releases")
  )
)

val root = project.in(file("."))
  .doNotPublish
  .aggregate(core)

lazy val core = project
  .configs(IntegrationTest)
  .enablePlugins(JavaServerAppPackaging)
  .settings(commonSettings)
  .settings(Defaults.itSettings)
  .settings(
    // TODO: Ideally, this should only grab the .sh files and not the .scala files
    unmanagedResourceDirectories in Compile ++= ( sourceDirectories in Compile ).value
  )
  .dependsOnRemote(
    jsch, commonsCodec, scalateCore, scalaGraphCore, scalaXml, sprayJson, timber.backend, timber.slf4j
  )
  .dependsOnRemote(Seq(scalatest, scalamock) map ( _ % "test, it" ):_*)
