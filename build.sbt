import sbt._

import Dependencies._

import Utils._

val commonSettings = Seq(
  name := "jibe",
  version := "1.0.0-SNAPSHOT",
  organization := "org.scalawag",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:existentials",
    "-target:jvm-1.6"
  ),
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
    akka.actor,
    akka.slf4j,
    commonsCodec,
    graph.core,
    graph.dot,
    jsch,
    scalateCore,
    scalaXml,
    spray.can,
    spray.json,
    spray.routing,
    timber.backend,
    timber.slf4j
  )
  .dependsOnRemote(Seq(
    scalatest,
    scalamock
  ) map ( _ % "test, it" ):_*)
