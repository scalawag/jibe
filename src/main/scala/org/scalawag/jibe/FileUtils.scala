package org.scalawag.jibe

import java.io._

object FileUtils {

  def writeFileWithOutputStream(f: File)(fn: OutputStream => Unit): Unit = {
    mkdir(f.getParentFile)

    val os = new FileOutputStream(f)
    try {
      fn(os)
    } finally {
      os.close()
    }
  }

  def writeFileWithPrintWriter(f: File)(fn: PrintWriter => Unit): Unit = {
    mkdir(f.getParentFile)

    val pw = new PrintWriter(new FileWriter(f))
    try {
      fn(pw)
    } finally {
      pw.close()
    }
  }

  def mkdir(dir: File) = {
    if ( dir.exists ) {
      if ( ! dir.isDirectory )
        throw new IllegalStateException(s"$dir already exists but is not a directory")
    } else {
      if ( ! dir.mkdirs() )
        throw new RuntimeException(s"failed to create directory $dir")
    }
    dir
  }

  implicit class FilePimper(f:File) {

    def /(name:String): File = new File(f,name)
    def /(name:Int): File = this / name.toString
  }
}
