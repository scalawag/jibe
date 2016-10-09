package org.scalawag.jibe.backend

import java.io.File
import org.scalawag.jibe.FileUtils._
import spray.json.RootJsonFormat

class FileBackedStatus[A, B <: RootJsonFormat[A]](file: File, initialValue: A)(implicit jsonFormat: B) {
  private[this] var _status: A = initialValue

  updateFile(initialValue) // write the initial value to disk

  def get: A = _status

  def mutate(fn: A => A) = synchronized {
    val oldValue = _status
    val newValue = fn(_status)
    if ( oldValue != newValue ) {
      updateFile(newValue)
      _status = newValue
    }
  }

  private[this] def updateFile(a: A) =
    writeFileWithPrintWriter(file) { pw =>
      pw.print(jsonFormat.write(a).prettyPrint)
    }
}
