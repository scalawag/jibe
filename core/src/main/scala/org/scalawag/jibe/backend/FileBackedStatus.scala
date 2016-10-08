package org.scalawag.jibe.backend

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

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

      // Fire a change event
      changeListeners.get.foreach(_.apply(oldValue, newValue))
    }
  }

  private[this] val changeListeners = new AtomicReference[Seq[(A, A) => Unit]](Seq.empty)

  def addChangeListener(listener: (A, A) => Unit) =
    changeListeners.getAndUpdate(new UnaryOperator[Seq[(A, A) => Unit]] {
      override def apply(t: Seq[(A, A) => Unit]) = t :+ listener
    })

  private[this] def updateFile(a: A) =
    writeFileWithPrintWriter(file) { pw =>
      pw.print(jsonFormat.write(a).prettyPrint)
    }
}
