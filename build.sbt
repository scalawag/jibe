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
    inConfig(CustomRuntime)(classpathConfiguration := Runtime),
    inConfig(CustomTest)(classpathConfiguration := Test),
    inConfig(CustomIntegrationTest)(classpathConfiguration := IntegrationTest),
    // Include macro config classes in the main jar built out of the compile config classes.
    inConfig(CustomCompile)(products ++= ( products in CustomMacro ).value),
    // Get rid of classifier on the artifact built by our new "compile" config (otherwise, it's "compile").
    artifact in (CustomCompile, packageBin) ~= ( _.copy(classifier = None) )
  )
  .dependsOnRemote(
    akka.actor,
    akka.slf4j,
    commonsCodec,
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
