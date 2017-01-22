package org.scalawag.jibe.outputs

import java.io.{File, PrintWriter}

import org.scalatest.FunSpec
import org.scalawag.jibe.FileUtils

import scala.concurrent.Await
import scala.concurrent.duration._

object OutputsTest extends FunSpec {
  // TODO: handle reporting/structure/metadata
  // TODO: make the implementation interface prettier

  def mapInput[A, B](fn: A => B) = new OpenMandate[A, B] {
    override def bind(in: MandateInput[A])(implicit runContext: RunContext) =
      new SimpleLogicMandate[A, B](in) {
        override protected[this] def dryRunLogic(in: A)(implicit runContext: RunContext) = Some(fn(in))
        override protected[this] def runLogic(in: A)(implicit runContext: RunContext) = fn(in)
      }
  }


  Logging.initialize()
/*
  it("should run two joined Mandates in parallel") {
    implicit val rc = new RunContext

    val seed = MandateInput.fromLiteral(())

    val af = new GenericOpenMandate[Unit, Int]("a", 0 seconds, { _ => None }, 3 seconds, { _ => 8})
    val a = af.bind(())
    val bf = new GenericOpenMandate[Int, String]("b", 0 seconds, { n => Some( ( "b" * n ) ) }, 3 seconds, { n => "b" * n })
    val m = bf.bind(a)

    println(Await.result(m.dryRunResult, Duration.Inf))

    println(Await.result(m.runResult, Duration.Inf))

    val xf = new GenericOpenMandate[Unit, Int]("x", 100 millis, { _ =>  Some(6) }, 1 second, { _ => 6 })
    val x = xf.bind(seed)

    val y = ( a join xf.bind(seed) )

    println(Await.result(y.dryRunResult, Duration.Inf))

    println(Await.result(y.runResult, Duration.Inf))
  }
*/

  def graph(mi: MandateInput[_], pw: PrintWriter): Unit = {
      pw.println("digraph RunnableGraph {")
      pw.println("  rankdir=LR")

      var vertices: Set[Int] = Set.empty

      def id(mi: MandateInput[_]) = System.identityHashCode(mi)

      def addToGraph(mi: MandateInput[_]): Unit = mi match {
        case cm: CompositeMandate[_] =>
          if ( ! vertices.contains(id(mi)) ) {
            val attrs = Map("shape" -> "cds", "label" -> cm.toString)
            val attrString = attrs.map { case (k, v) =>
              s"""${k}="${v}""""
            }.mkString("[",",","]")

            pw.println(s""""${id(mi)}"$attrString""")

            mi.inputs foreach addToGraph

            mi.inputs foreach { from =>
              pw.println(s""""${id(from)}" -> "${id(mi)}"""")
            }
          }

        case m: Mandate[_] =>
          if ( ! vertices.contains(id(mi)) ) {
            val attrs = Map("shape" -> "box", "label" -> m.toString)
            val attrString = attrs.map { case (k, v) =>
              s"""${k}="${v}""""
            }.mkString("[",",","]")

            pw.println(s""""${id(mi)}"$attrString""")

            mi.inputs foreach addToGraph

            mi.inputs foreach {
              case from: CompositeMandate[_] =>
                pw.println(s""""${id(from)}" -> "${id(mi)}"""")
              case from: Mandate[_] =>
                pw.println(s""""${id(from)}" -> "${id(mi)}"""")
              case _ =>
            }
          }

        case _ => None
      }

    addToGraph(mi)
    pw.println("}")
  }

  def main(args: Array[String]): Unit = {
    implicit val rc = new RunContext

    def dumpInputs(mi: MandateInput[_], depth: Int = 0): Unit = mi match {
      case m: Mandate[_] =>
        println(" " * depth + m)
        m.inputs foreach { i =>
          dumpInputs(i, depth + 2)
        }
      case m: CompositeMandate[_] =>
        println(" " * depth + m)
        m.inputs foreach { i =>
          dumpInputs(i, depth + 2)
        }
      case _ =>
        mi.inputs foreach { i =>
          dumpInputs(i, depth)
        }
    }

    val j = MandateLibrary.InstallSoftware.bind()

    dumpInputs(j)
    FileUtils.writeFileWithPrintWriter(new File("graph.dot"))(graph(j, _))

    println(Await.result(j.dryRunResult, Duration.Inf))
    println(Await.result(j.runResult, Duration.Inf))

    println(Await.result(j.runResult, Duration.Inf))
  }
}
