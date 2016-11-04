package org.scalawag.jibe.mandate

import scala.concurrent.duration._
import org.scalawag.jibe.mandate.command.CommandArgument

@CommandArgument
case class Package(name: String,
                   version: Option[String] = None)

object Package {
  implicit def fromString(name: String) = Package(name)
  def apply(name: String, version: String): Package = new Package(name, Some(version))
}

case class InstallPackage(pkg: Package) extends StatelessMandate with MandateHelpers {
  override val description = Some(s"install package: ${pkg.name}" )

  override def consequences = Iterable(PackageResource(pkg.name))

  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    runCommand(command.UpdateAptGet(1.day))
    runCommand(command.IsPackageInstalled(pkg))
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    runCommand(command.UpdateAptGet(1.day))
    runCommand(command.InstallPackage(pkg))
  }
}

case class InstallAptKey(keyserver: String, fingerprint: String) extends StatelessMandate with MandateHelpers {
  override val description = Some(s"install apt key: ${fingerprint}" )

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    runCommand(command.IsAptKeyInstalled(fingerprint))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.InstallAptKey(keyserver, fingerprint))
}