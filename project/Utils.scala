/*
 * Contains code adapted from Lightbend Activator https://github.com/typesafehub/activator
 */
import sbt._
import Keys._

object Utils {

  implicit class DoNotPublish(val project: Project) extends AnyVal {
    def doNotPublish: Project = {
      project.settings(
        publish := { streams.value.log(s"publish disabled for ${name.value}") },
        publishLocal := { streams.value.log(s"publishLocal disabled for ${name.value}") }
      )
    }
  }

  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  final class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }

  // DSL for adding source dependencies to projects.
  def dependsOnSource(dir: String): Seq[Setting[_]] = {
    import Keys._
    Seq(unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, baseDirectory) { (srcDirs, base) => (base / dir / "src/main/scala") +: srcDirs },
      unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test, baseDirectory) { (srcDirs, base) => (base / dir / "src/test/scala") +: srcDirs })
  }

  implicit def p2source(p: Project): SourceDepHelper = new SourceDepHelper(p)
  final class SourceDepHelper(p: Project) {
    def dependsOnSource(dir: String): Project =
      p.settings(Utils.dependsOnSource(dir):_*)
  }
}
