import sbt._
import Dependencies._
import Utils._

val commonSettings = Seq(
  version := "0.1-SNAPSHOT",
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
  ),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  credentials ++= {
    // Travis CI will have the credentials in these environment variables, so this enables automated publishing.
    for {
      username <- Option(System.getenv().get("SONATYPE_USERNAME"))
      password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
    } yield {
      Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)
    }
  }.toSeq
)

val root = project.in(file("."))
  .settings(commonSettings)
  .doNotPublish
  .aggregate(core)

lazy val core = project
  .enablePlugins(JavaServerAppPackaging)
  .compileMacros
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    name := "jibe-core"
  )
  .dependsOnRemote(
    akka.actor,
    akka.slf4j,
    commonsCodec,
    css,
    druthers,
    graph.core,
    graph.dot,
    jsch,
    macroParadise,
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
