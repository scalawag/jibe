package org.scalawag.jibe

import org.apache.commons.codec.digest.DigestUtils

object MD5 {
  def apply(s: String) = DigestUtils.md5Hex(s).toLowerCase
}
