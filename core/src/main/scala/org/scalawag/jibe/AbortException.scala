package org.scalawag.jibe

case class AbortException(message: String) extends Exception(message)