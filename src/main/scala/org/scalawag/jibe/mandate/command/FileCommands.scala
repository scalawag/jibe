package org.scalawag.jibe.mandate.command

import java.io.File

/** Returns true if the remote file exists and has the specified length in bytes.  Returns false otherwise. */

case class IsRemoteFileLength(file: File, length: Long) extends BooleanCommand

/** Returns true if the remote file exists and has the MD5 checksum specified in hex.  Returns false otherwise. */

case class IsRemoteFileMD5(file: File, md5: String) extends BooleanCommand

/** Returns true if the local file has been copied to the remote file.  Returns false otherwise. */

case class SendLocalFile(local: File, remote: File) extends UnitCommand
