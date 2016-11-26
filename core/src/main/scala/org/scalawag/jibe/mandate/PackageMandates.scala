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

case class InstallPackage(pkg: Package) extends StatelessMandate with MandateHelpers with CaseClassMandate {
  override val label = s"install package: ${pkg.name}"

  override val decorations = Set[MultiTreeDecoration](Consequences(PackageResource(pkg.name)))

  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    runCommand(command.UpdateAptGet(1.day))
    runCommand(command.IsPackageInstalled(pkg))
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    runCommand(command.UpdateAptGet(1.day))
    runCommand(command.InstallPackage(pkg))
  }
}


case class InstallAptKey(keyServer: String, keyFingerprint: String) extends StatelessMandate with MandateHelpers with CaseClassMandate {

  override val label = s"install apt key: ${keyFingerprint}"

  override def isActionCompleted(implicit context: MandateExecutionContext) =
    runCommand(command.IsAptKeyInstalled(keyFingerprint))

  override def takeAction(implicit context: MandateExecutionContext) =
    runCommand(command.InstallAptKey(keyServer, keyFingerprint))
}
