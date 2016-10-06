package org.scalawag.jibe.mandate.command

@CommandArgument
case class ExitWithArgument(exitCode: Int) extends IntCommand
