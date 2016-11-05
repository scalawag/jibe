package org.scalawag.jibe

import java.io._
import spray.json._

object FileUtils {

  def writeJson[A](f:File, jsonable: A)(implicit format: RootJsonFormat[A]) =
    writeFileWithPrintWriter(f) { pw =>
      pw.println(jsonable.toJson.prettyPrint)
    }

  def writeFileWithOutputStream(f: File)(fn: OutputStream => Unit): Unit = {
    Option(f.getParentFile).foreach(mkdir)

    val os = new FileOutputStream(f)
    try {
      fn(os)
    } finally {
      os.close()
    }
  }

  def writeFileWithPrintWriter(f: File)(fn: PrintWriter => Unit): Unit = {
    Option(f.getParentFile).foreach(mkdir)

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

  implicit def toFile(filename: String) = new File(filename)

  implicit class FilePimper(f:File) {

    def /(name:String): File = new File(f,name)
    def /(name:Int): File = this / name.toString
  }
}
