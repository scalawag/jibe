/*
 * Contains code adapted from Lightbend Activator https://github.com/typesafehub/activator
 */
import sbt._
import Keys._

object Utils {

  implicit class MacroSupport(val project: Project) {
    // Customize all configurations to enable macros to compile before the main bulk of the source.
    val CustomMacro = config("macro")
    val CustomCompile = config("compile") extend(CustomMacro)
    val CustomRuntime = config("runtime") extend(CustomCompile)
    val CustomTest = config("test") extend(CustomRuntime)
    val CustomIntegrationTest = config("it") extend(CustomRuntime)

    def compileMacros: Project = {
      project.settings(
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
      .overrideConfigs(CustomMacro, CustomCompile, CustomRuntime, CustomTest, CustomIntegrationTest)
    }
  }

  implicit class DoNotPublish(val project: Project) extends AnyVal {
    def doNotPublish: Project = {
      project.settings(
        publish := { streams.value.log(s"publish disabled for ${name.value}") },
        publishLocal := { streams.value.log(s"publishLocal disabled for ${name.value}") }
      )
    }
  }

  // DSL for adding remote deps like local deps.
  implicit class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }

  // DSL for adding source dependencies to projects.
  def dependsOnSource(dir: String): Seq[Setting[_]] = {
    import Keys._
    Seq(unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, baseDirectory) { (srcDirs, base) => (base / dir / "src/main/scala") +: srcDirs },
      unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test, baseDirectory) { (srcDirs, base) => (base / dir / "src/test/scala") +: srcDirs })
  }

  implicit class SourceDepHelper(p: Project) {
    def dependsOnSource(dir: String): Project =
      p.settings(Utils.dependsOnSource(dir):_*)
  }
}
