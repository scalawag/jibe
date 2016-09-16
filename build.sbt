name := "jibe"

version := "1.0.0-SNAPSHOT"

organization := "org.scalawag"

scalaVersion := "2.11.8"

resolvers ++= Seq (
  "JAnalyse Repository" at "http://www.janalyse.fr/repository/",
  "sonatype-oss-snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies ++= Seq(
  "com.jcraft" % "jsch" % "0.1.54",
  "commons-codec" % "commons-codec" % "1.10",
  "org.scala-graph" %% "graph-core" % "1.11.2",
  "org.scala-lang" % "scala-xml" % "2.11.0-M4",
  "io.spray" %% "spray-json" % "1.3.2",
  "org.scalawag.timber" %% "timber-backend" % "0.6.0-SNAPSHOT",
  "org.scalawag.timber" %% "slf4j-over-timber" % "0.6.0-SNAPSHOT"
)

libraryDependencies ++= Seq (
  "org.scalatest" %% "scalatest" % "3.0.0",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2"
) map ( _ % "test" )

enablePlugins(JavaServerAppPackaging)

