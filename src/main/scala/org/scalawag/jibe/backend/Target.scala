package org.scalawag.jibe.backend

case class Target(hostname: String,
                  username: String,
                  password: String,
                  port: Int,
                  commander: Commander,
                  sudo: Boolean = false)
