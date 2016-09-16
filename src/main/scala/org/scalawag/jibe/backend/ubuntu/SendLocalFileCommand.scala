package org.scalawag.jibe.backend.ubuntu

import java.io.{File, FileInputStream}
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend.{Command, SSHConnectionInfo}

class SendLocalFileCommand(src: File, dst: File) extends Command {

  // TODO: This could be sped up by checking for the presence of the file (and/or length) prior to calculating the MD5 locally.

  override def test(sshConnectionInfo: SSHConnectionInfo, dir: File) = {
    // Calculate MD5 of local file
    val fis = new FileInputStream(src)
    val localMd5 =
      try {
        DigestUtils.md5Hex(fis).toLowerCase
      } finally {
        fis.close()
      }

    ssh(sshConnectionInfo, s"""test -r "$dst" -a $$( md5sum "$dst" | awk '{ print $$1 }' ) == $localMd5""", dir)
  }

  override def perform(sshConnectionInfo: SSHConnectionInfo, dir: File) = scp(sshConnectionInfo, src, dst, dir)
}
