package org.scalawag.jibe.backend

case class SSHConnectionInfo(host: String, username: String, password: String, port: Int, sudo: Boolean = false)
