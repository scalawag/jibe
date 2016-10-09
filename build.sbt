import sbt._
import sbt.dsl._
import Dependencies._
import Utils._

lazy val Macro = config("macro")

lazy val CustomCompile = config("compile") extend(Macro)

val commonSettings = Seq(
  version := "1.0.0-SNAPSHOT",
  organization := "org.scalawag",
  scalaVersion := "2.11.8",
  parallelExecution in IntegrationTest := false,
  resolvers ++= Seq (
    "JAnalyse Repository" at "http://www.janalyse.fr/repository/",
    Resolver.sonatypeRepo("releases")
  )
)

lazy val core = project
  .configs(IntegrationTest, Macro, CustomCompile)
  .enablePlugins(JavaServerAppPackaging)
  .settings(commonSettings)
  .settings(Defaults.itSettings)
  .settings(
    name := "jibe-core",
    inConfig(Macro) {
      Defaults.compileSettings ++
        Seq(
          scalaSource := baseDirectory.value / "src" / "macro" / "scala",
          classpathConfiguration := CustomCompile
        )
    },
    // TODO: Ideally, this should only grab the .sh files and not the .scala files
    unmanagedResourceDirectories in Compile ++= ( sourceDirectories in Compile ).value
  )
  .dependsOnRemote(
    jsch,
    commonsCodec,
    scalateCore,
    scalaGraphCore,
    scalaXml,
    sprayJson,
    timber.backend,
    timber.slf4j,
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
  .dependsOnRemote(Seq(scalatest, scalamock) map ( _ % "test, it" ):_*)
