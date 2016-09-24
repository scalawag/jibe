package org.scalawag.jibe.mandate

import java.io.{FileInputStream, File}
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend.FileResource
import org.scalawag.jibe.mandate.command.{IsRemoteFileMD5, IsRemoteFileLength}

case class SendLocalFile(localPath: File, remotePath: File) extends CheckableMandate {
  override val description = Some(s"send local file $localPath -> $remotePath")
  override def consequences = Iterable(FileResource(remotePath.getAbsolutePath))

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean = {
    import context._

    // Do a quick check that it exists and is the right length before proceeding.

    if (runCommand("isRemoteFileRightLength", IsRemoteFileLength(remotePath, localPath.length))) {
      log.debug("remote file exists and has the correct length, calculating checksum")

      val fis = new FileInputStream(localPath)
      val localMd5 =
        try {
          DigestUtils.md5Hex(fis).toLowerCase
        } finally {
          fis.close()
        }

      val answer = runCommand("isRemoteFileRightChecksum", IsRemoteFileMD5(remotePath, localMd5))
      log.debug(s"checksum is ${ if ( answer ) "" else "not " }correct")
      answer

    } else {
      log.debug("remote file has a different length than expected")
      false
    }
  }

  override def takeAction(implicit context: MandateExecutionContext): Unit = {
    runCommand("takeAction", command.SendLocalFile(localPath, remotePath))
  }

}
