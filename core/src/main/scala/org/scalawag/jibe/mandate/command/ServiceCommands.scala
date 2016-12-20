package org.scalawag.jibe.mandate.command

import org.scalawag.jibe.mandate.Service

@CommandArgument
case class IsServiceRunning(service: Service) extends BooleanCommand

@CommandArgument
case class StartService(service: Service) extends UnitCommand

@CommandArgument
case class StopService(service: Service) extends UnitCommand

@CommandArgument
case class RestartService(service: Service) extends UnitCommand