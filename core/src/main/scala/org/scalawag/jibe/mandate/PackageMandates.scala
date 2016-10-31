package org.scalawag.jibe.mandate

import scala.concurrent.duration._
import org.scalawag.jibe.mandate.command.CommandArgument
import org.scalawag.jibe.multitree._

@CommandArgument
case class Package(name: String,
                   version: Option[String] = None)

object Package {
  implicit def fromString(name: String) = Package(name)
  def apply(name: String, version: String): Package = new Package(name, Some(version))
}

object InstallPackage {
  case class InstallPackage(pkg: Package) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) = {
      runCommand(command.UpdateAptGet(1.day))
      runCommand(command.IsPackageInstalled(pkg))
    }

    override def takeAction(implicit context: MandateExecutionContext) = {
      runCommand(command.UpdateAptGet(1.day))
      runCommand(command.InstallPackage(pkg))
    }
  }

  def apply(pkg: Package) = MultiTreeLeaf(
    mandate = new InstallPackage(pkg),
    name = Some(s"install package: ${pkg.name}" ),
    decorations = Set[MultiTreeDecoration](Consequences(PackageResource(pkg.name)))
  )
}

object InstallAptKey {
  case class InstallAptKey(keyserver: String, fingerprint: String) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) =
      runCommand(command.IsAptKeyInstalled(fingerprint))

    override def takeAction(implicit context: MandateExecutionContext) =
      runCommand(command.InstallAptKey(keyserver, fingerprint))
  }

  def apply(keyserver: String, fingerprint: String) = MultiTreeLeaf(
    mandate = new InstallAptKey(keyserver, fingerprint),
    name = Some(s"install apt key: ${fingerprint}" )
  )
}