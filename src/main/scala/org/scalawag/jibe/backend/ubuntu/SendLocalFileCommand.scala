package org.scalawag.jibe.backend.ubuntu

import java.io.{File, FileInputStream}
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend.{Command, SSHConnectionInfo}
import org.scalawag.jibe.FileUtils._

class SendLocalFileCommand(src: File, dst: File) extends Command {

  // TODO: This could be sped up by checking for the presence of the file (and/or length) prior to calculating the MD5 locally.

  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) = {

    // Do a quick check that it exists and is the right length before proceeding.

    val ec = ssh(sshConnectionInfo,
      s"""
         |llen=${src.length}
         |rpath="${dst}"
         |if [ ! -r "$$rpath" ]; then
         |  echo "remote file does not exist"
         |  exit 1
         |else
         |  rlen=$$( stat -c %s "$$rpath" )
         |  if [ $$llen != $$rlen ]; then
         |    echo "len(local)  = $$llen"
         |    echo "len(remote) = $$rlen"
         |    exit 1
         |  fi
         |fi
      """.stripMargin.trim, dir / "length_check")

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

      ssh(sshConnectionInfo,
        s"""
           |lmd5=${localMd5}
           |rpath="${dst}"
           |if [ ! -r "$$rpath" ]; then
           |  echo "remote file does not exist"
           |  exit 1
           |else
           |  rmd5=$$( md5sum "$$rpath" | awk '{ print $$1 }' )
           |  if [ $$lmd5 != $$rmd5 ]; then
           |    echo "md5(local)  = $$lmd5"
           |    echo "md5(remote) = $$rmd5"
           |    exit 1
           |  fi
           |fi
        """.stripMargin.trim, dir / "content_check")
    }
  }

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) = scp(sshConnectionInfo, src, dst, dir)
}
