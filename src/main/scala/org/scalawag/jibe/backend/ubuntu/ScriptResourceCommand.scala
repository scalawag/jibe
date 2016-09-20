package org.scalawag.jibe.backend.ubuntu

import java.io.File
import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import org.scalawag.jibe.backend.{Command, Target}

import scala.reflect.ClassTag

trait ScriptResourceCommand extends Command {
  protected def getScriptContext: Map[String, String]

  protected def caseClassToContext[T: ClassTag](name: String, cc: T) = {
    val mirror = currentMirror.reflect(cc)
    mirror.symbol.typeSignature.members.collect {
      case s: TermSymbol if s.isCaseAccessor => mirror.reflectField(s)
    } map { r =>

      def stringify(a: Any): String =
        a match {
          case s: String => s
          case n: Int => n.toString
          case b: Boolean => if ( b ) "t" else ""
          case i: Iterable[_] => i.map(stringify).mkString(" ")
          case Some(x) => stringify(x)
          case None => ""
        }

      s"${name}_${r.symbol.name.toString.trim}" -> stringify(r.get)
    } toMap
  }

  private[this] val scriptPrefix = this.getClass.getSimpleName

  override def test(target: Target, dir: File) =
    sshResource(target, s"${scriptPrefix}_test.sh", getScriptContext, dir)

  override def perform(target: Target, dir: File) =
    sshResource(target, s"${scriptPrefix}_perform.sh", getScriptContext, dir)
}
