package org.scalawag.jibe.mandate

import java.io.{FileInputStream, File}
import org.scalawag.jibe.FileUtils._
import org.apache.commons.codec.digest.DigestUtils
import org.scalawag.jibe.backend.{Commander, FileResource}
import org.scalawag.jibe.mandate.command.{IsRemoteFileMD5, IsRemoteFileLength}

case class SendLocalFile(localPath: File, remotePath: File) extends CheckableMandate {
  override val description = Some(s"send local file $localPath -> $remotePath")
  override def consequences = Iterable(FileResource(remotePath.getAbsolutePath))

  override def isActionCompleted(commander: Commander, resultsDir: File): Boolean = {
    // Do a quick check that it exists and is the right length before proceeding.

    if (commander.execute(resultsDir / "test/isRemoteFileRightLength", IsRemoteFileLength(remotePath, localPath.length))) {

      val fis = new FileInputStream(localPath)
      val localMd5 =
        try {
          DigestUtils.md5Hex(fis).toLowerCase
        } finally {
          fis.close()
        }

      commander.execute(resultsDir / "test/isRemoteFileRightChecksum", IsRemoteFileMD5(remotePath, localMd5))
    } else {
      false
    }
  }

  override def takeAction(commander: Commander, resultsDir: File): Unit = {
    commander.execute(resultsDir / "takeAction", command.SendLocalFile(localPath, remotePath))
  }

}
