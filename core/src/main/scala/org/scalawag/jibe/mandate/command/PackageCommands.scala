package org.scalawag.jibe.mandate.command

import org.scalawag.jibe.mandate.Package
import scala.concurrent.duration._

/** Returns true if the package is installed at the specified version.
  * If no version is specified, only returns true if it is the most recent version.
  */

@CommandArgument
case class IsPackageInstalled(pkg: Package) extends BooleanCommand

/** Returns true if the package is installed at the specified version or the most recent if not specified.
  */

@CommandArgument
case class InstallPackage(pkg: Package) extends UnitCommand

/** Runs apt-get update if the cache is old enough
  */

@CommandArgument
case class UpdateAptGet(refreshInterval: Duration) extends UnitCommand

@CommandArgument
case class IsAptKeyInstalled(fingerprint: String) extends BooleanCommand

@CommandArgument
case class InstallAptKey(keyserver: String, fingerprint: String) extends UnitCommand

