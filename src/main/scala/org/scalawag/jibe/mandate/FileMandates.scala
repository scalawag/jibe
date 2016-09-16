package org.scalawag.jibe.mandate

import java.io.File

import org.scalawag.jibe.backend.FileResource

case class SendLocalFile(localPath: File, remotePath: File) extends Mandate {
  override val description = Some(s"send local file $localPath -> $remotePath")
  override def consequences = Iterable(FileResource(remotePath.getAbsolutePath))
}
