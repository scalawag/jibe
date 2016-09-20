package org.scalawag.jibe.backend.ubuntu

import java.io.{File, FileInputStream}
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend.{Command, Target}
import org.scalawag.jibe.FileUtils._

class SendLocalFileCommand(src: File, dst: File) extends Command {

  override def test(target: Target, dir: File) = {

    // Do a quick check that it exists and is the right length before proceeding.

    val ec = sshResource(target, "SendLocalFileCommand_length_check.sh", Map("llen" -> src.length, "rpath" -> dst), dir / "length_check")

    if ( ec != 0 ) {
      // File is missing or length is incorrect.  It failed the test without checksum calculation.
      ec
    } else {
      // Length is correct.  Now check the content with an MD5 (which can take longer to calculate locally).

      val fis = new FileInputStream(src)
      val localMd5 =
        try {
          DigestUtils.md5Hex(fis).toLowerCase
        } finally {
          fis.close()
        }

      sshResource(target, "SendLocalFileCommand_content_check.sh", Map("lmd5" -> localMd5, "rpath" -> dst), dir / "content_check")
    }
  }

  override def perform(target: Target, dir: File) = scp(target, src, dst, dir)
}
