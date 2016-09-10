package org.scalawag.jibe

import java.io.{FileFilter, File}

object FileUtils {
  implicit class FilePimper(f:File) {

    def /(name:String): File = new File(f,name)
    def /(name:Int): File = this / name.toString
  }
}
