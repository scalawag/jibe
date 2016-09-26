package org.scalawag.jibe.mandate.command

// Supported parameter types here are determined by the Commander

sealed trait Command[T]

trait IntCommand extends Command[Int]

trait UnitCommand extends Command[Unit]

trait BooleanCommand extends Command[Boolean]
