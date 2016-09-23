package org.scalawag.jibe.mandate

import java.io.{FileInputStream, File}
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend.FileResource
import org.scalawag.jibe.mandate.command.{IsRemoteFileMD5, IsRemoteFileLength}

case class SendLocalFile(localPath: File, remotePath: File) extends CheckableMandate {
  override val description = Some(s"send local file $localPath -> $remotePath")
  override def consequences = Iterable(FileResource(remotePath.getAbsolutePath))

  override def isActionCompleted(implicit context: MandateExecutionContext): Boolean = {
    // Do a quick check that it exists and is the right length before proceeding.

    if (runCommand("isRemoteFileRightLength", IsRemoteFileLength(remotePath, localPath.length))) {

      val fis = new FileInputStream(localPath)
      val localMd5 =
        try {
          DigestUtils.md5Hex(fis).toLowerCase
        } finally {
          fis.close()
        }

      runCommand("isRemoteFileRightChecksum", IsRemoteFileMD5(remotePath, localMd5))
    } else {
      false
    }
  }

  override def takeAction(implicit context: MandateExecutionContext): Unit = {
    runCommand("takeAction", command.SendLocalFile(localPath, remotePath))
  }

}
