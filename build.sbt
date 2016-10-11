import sbt._
import sbt.dsl._
import Dependencies._
import Utils._

lazy val Macro = config("macro")

lazy val CustomCompile = config("compile") extend(Macro)

lazy val CustomRuntime = config("runtime") extend(CustomCompile)

//lazy val CustomTest = config("test") extend(CustomRuntime)

//lazy val CustomIntegrationTest = config("it") extend(CustomRuntime)

val commonSettings = Seq(
  version := "1.0.0-SNAPSHOT",
  scalacOptions += "-Ylog-classpath",
  organization := "org.scalawag",
  scalaVersion := "2.11.8",
  parallelExecution in IntegrationTest := false,
  resolvers ++= Seq (
    "JAnalyse Repository" at "http://www.janalyse.fr/repository/",
    Resolver.sonatypeRepo("releases")
  )
)

lazy val core = project
  .enablePlugins(JavaServerAppPackaging)
  .overrideConfigs(Macro, CustomCompile, CustomRuntime, IntegrationTest)
//  .overrideConfigs(Macro, CustomCompile, CustomRuntime, CustomTest, IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    name := "jibe-core",
//    ivyConfigurations := overrideConfigs(Macro, CustomCompile, CustomRuntime, CustomTest)(ivyConfigurations.value),
    inConfig(Macro) {
      Defaults.compileSettings ++
      Seq(
        classpathConfiguration := CustomCompile
      )
    },
    // TODO: Ideally, this should only grab the .sh files and not the .scala files
    unmanagedResourceDirectories in CustomCompile ++= ( sourceDirectories in CustomCompile ).value
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
