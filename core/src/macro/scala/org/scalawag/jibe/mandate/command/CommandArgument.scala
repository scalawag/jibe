package org.scalawag.jibe.mandate.command

import scala.language.experimental.macros
import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.reflect.macros.whitebox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class CommandArgument extends StaticAnnotation {
  def macroTransform(annottees: Any*):Any = macro CommandArgument.impl
}

object CommandArgument {
  trait Value
  trait AtomicValue extends Value
  case class StringValue(val value: String) extends AtomicValue
  case class BooleanValue(val value: Boolean) extends AtomicValue
  case class LongValue(val value: Long) extends AtomicValue
  case class TraversableValue[A <: AtomicValue](val value: Traversable[A]) extends Value
  case class StructureValue(val values: Map[String, Value]) extends Value

  trait ToValue[-A, +B <: Value] {
    def toValue(a: A): Option[B]
  }

  object ToValue {
    def toValue[A](a: A)(implicit toValue: ToValue[A, Value]) = toValue.toValue(a)
  }

  trait ToStructure {
    def toStructure: StructureValue
  }

  val debug = Option(System.getProperty("org.scalawag.jibe.macros.debug")).isDefined

  def impl(c:Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    def macroDecls(className: TypeName, fields: Iterable[ValDef]) = {
      val fieldExprs = fields map {
//        case q"$_ val $name:$tpeIdent = $init" =>
        case q"$_ val ${fn: TermName}: ${tpeIdent: Tree} = $_" =>

//          val classType: Type = c.typecheck(className, c.TYPEmode).tpe
          // TODO: this needs to resolve non-top-level classes to be able to put them into objects.  Right now, it's just using the name and that's failing.
          val ft: Type = c.typecheck(tpeIdent, c.TYPEmode).tpe

          val fs = ft.typeSymbol
          val fl = Literal(Constant(fn.toString))
          if ( fs.asType.toType <:< weakTypeOf[ToStructure] ) {
            q"""this.${fn.toTermName}.toStructure.values.map { case (k, v) => $fl + "_" + k -> v };"""
          } else {
            q"""org.scalawag.jibe.mandate.command.CommandArgument.ToValue.toValue(this.${fn.toTermName}).toIterable.flatMap(v => Iterable($fl -> v))"""
          }
      }

        //    if ( ! sym.isClass || ! sym.asClass.isCaseClass )
        //      c.abort(c.enclosingPosition, s"type argument must be a case class")

      val ret =
        q"""
          override def toStructure = new org.scalawag.jibe.mandate.command.CommandArgument.StructureValue(Iterable(..$fieldExprs).flatten.toMap)
        """
//      if ( debug ) println(ret)
      ret
    }

    annottees map (_.tree) toList match {
      // Handle case class with an existing companion object.
      case List(q"""case class ${className:TypeName}(..${fields: Iterable[ValDef]}) extends ..$parents { ..$body }""", companion) =>

        val asStructureMethod = macroDecls(className, fields)
        val ret =
          c.Expr[Any](q"""
            case class $className( ..$fields) extends ..$parents with org.scalawag.jibe.mandate.command.CommandArgument.ToStructure {
              $asStructureMethod
              ..$body
            }
            $companion
          """)
        if ( debug ) println(ret)
        ret

      // Handle case class without a companion object.
      case List(q"""case class ${className:TypeName}(..${fields: Iterable[ValDef]}) extends ..$parents { ..$body }""") =>

        val asStructureMethod = macroDecls(className, fields)
        val ret = c.Expr[Any](q"""
          case class $className( ..$fields) extends ..$parents with org.scalawag.jibe.mandate.command.CommandArgument.ToStructure {
            $asStructureMethod
            ..$body
          }
        """)
        if ( debug ) println(ret)
        ret

      case x =>
        c.abort(c.enclosingPosition, "only case classes can be annotated with @CommandArgument")
    }
  }
}

