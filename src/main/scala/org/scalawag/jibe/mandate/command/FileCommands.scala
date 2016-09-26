package org.scalawag.jibe.mandate.command

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}

/** Returns true if the remote file exists and has the specified length in bytes.  Returns false otherwise. */

case class IsRemoteFileLength(remotePath: File, length: Long) extends BooleanCommand

/** Returns true if the remote file exists and has the MD5 checksum specified in hex.  Returns false otherwise. */

case class IsRemoteFileMD5(remotePath: File, md5: String) extends BooleanCommand

/** Returns true if the local file has been copied to the remote file.  Returns false otherwise. */

case class WriteRemoteFile(remotePath: File, content: FileContent) extends UnitCommand

trait FileContent {
  // must be closed by caller
  def openInputStream: InputStream
  def length: Long
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

case class FileContentFromArray(bytes: Array[Byte]) extends FileContent {
  override def openInputStream = new ByteArrayInputStream(bytes)
  override def length = bytes.length
  override val toString = s"array(${bytes.length} bytes)"
}