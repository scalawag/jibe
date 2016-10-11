import sbt._
import Dependencies._
import Utils._

val CustomMacro = config("macro")

val CustomCompile = config("compile") extend(CustomMacro)

val CustomRuntime = config("runtime") extend(CustomCompile)

val CustomTest = config("test") extend(CustomRuntime)

val CustomIntegrationTest = config("it") extend(CustomRuntime)

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
  .enablePlugins(JavaServerAppPackaging)
  .overrideConfigs(CustomMacro, CustomCompile, CustomRuntime, CustomTest, CustomIntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    name := "jibe-core",
    inConfig(CustomMacro)(Defaults.compileSettings),
    inConfig(CustomCompile)(Defaults.compileSettings),
    inConfig(CustomRuntime)(Defaults.configSettings),
    inConfig(CustomTest)(Defaults.testSettings),
    inConfig(CustomIntegrationTest)(Defaults.testSettings),
    inConfig(CustomMacro)(classpathConfiguration := CustomCompile),
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
