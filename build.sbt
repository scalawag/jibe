import sbt._
import Dependencies._
import Utils._

// Customize all configurations to enable macros to compile before the main bulk of the source.

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
    // Make all of our custom configurations work the way their namesakes do.
    inConfig(CustomMacro)(Defaults.compileSettings),
    inConfig(CustomCompile)(Defaults.compileSettings),
    inConfig(CustomRuntime)(Defaults.configSettings),
    inConfig(CustomTest)(Defaults.testSettings),
    inConfig(CustomIntegrationTest)(Defaults.testSettings),
    // Don't know what this does or why it is needed, but it is.
    inConfig(CustomMacro)(classpathConfiguration := CustomCompile),
    // Include macro config classes in the main jar built out of the compile config classes.
    inConfig(CustomCompile)(products ++= ( products in CustomMacro ).value),
    // Get rid of classifier on the artifact built by our new "compile" config (otherwise, it's "compile").
    artifact in (CustomCompile, packageBin) ~= ( _.copy(classifier = None) )
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
