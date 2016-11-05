package org.scalawag.jibe.multitree

case class MultiTreeId(fingerprint: String, serial: Int) extends Ordered[MultiTreeId] {
  override def compare(that: MultiTreeId) = this.fingerprint.compare(that.fingerprint) match {
    case 0 => this.serial.compare(that.serial)
    case n => n
  }
  override def toString = "%s-%04d".format(fingerprint, serial)
}

object MultiTreeId {
  def apply(s: String): MultiTreeId = {
    if ( s.length != 37 )
      throw new IllegalArgumentException("malformed MultiTreeId")
    val dash = s.indexOf('-')
    if ( dash != 32 )
      throw new IllegalArgumentException("malformed MultiTreeId")
    val fingerprint = s.take(32)
    if ( ! """[a-f0-9]{32}""".r.pattern.matcher(fingerprint).matches() )
      throw new IllegalArgumentException("malformed MultiTreeId")
    val serial = s.substring(33).dropWhile(_ == '0') match {
      case "" => 0
      case n => n.toInt
    }
    MultiTreeId(fingerprint, serial)
  }
}