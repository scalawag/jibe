package org.scalawag.jibe.multitree

abstract class Resource(override val scope: Scope = CommanderScope) extends Scoped

case class UserResource(name: String) extends Resource
case class GroupResource(name: String) extends Resource
case class FileResource(path: String) extends Resource
case class PackageResource(name: String) extends Resource
case class ServiceResource(name: String) extends Resource
case class ProcessResource(name: String) extends Resource
