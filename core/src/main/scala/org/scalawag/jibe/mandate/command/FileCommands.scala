package org.scalawag.jibe.mandate.command

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}

import org.apache.commons.codec.digest.DigestUtils

/** Returns true if the remote file exists and has the specified length in bytes.  Returns false otherwise. */

@CommandArgument
case class IsRemoteFileLength(file: File, length: Long) extends BooleanCommand

/** Returns true if the remote file exists and has the MD5 checksum specified in hex.  Returns false otherwise. */

@CommandArgument
case class IsRemoteFileMD5(file: File, md5: String) extends BooleanCommand

/** Returns true if the local file has been copied to the remote file.  Returns false otherwise. */

case class WriteRemoteFile(remotePath: File, content: FileContent) extends UnitCommand

trait FileContent {
  // must be closed by caller
  def openInputStream: InputStream
  def length: Long
  lazy val md5 = {
    val fis = openInputStream
    try {
      DigestUtils.md5Hex(fis).toLowerCase
    } finally {
      fis.close()
    }
  }
}

object FileContent {
  implicit def apply(f: File) = new FileContentFromFile(f)
  implicit def apply(a: Array[Byte]) = new FileContentFromArray(a)
  implicit def apply(s: String) = new FileContentFromArray(s.getBytes) // TODO: charset
}

case class FileContentFromFile(file: File) extends FileContent {
  override def openInputStream = new FileInputStream(file)
  override def length = file.length
  override val toString = s"file($file)"
}

case class FileContentFromArray(bytes: Seq[Byte]) extends FileContent {
  override def openInputStream = new ByteArrayInputStream(bytes.toArray)
  override def length = bytes.length
  override val toString = s"array(${bytes.length} bytes)"
}