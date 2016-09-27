package org.scalawag.jibe.mandate.command

// Supported parameter types here are determined by the Commander.  Subclasses should derive from one of the subclasses
// here and not from Command directly.  It's important that we have some run-time information when the commands are
// run and type erasure prevents that if you extend Command directly.

sealed trait Command[T]

trait IntCommand extends Command[Int]

trait UnitCommand extends Command[Unit]

trait BooleanCommand extends Command[Boolean]
