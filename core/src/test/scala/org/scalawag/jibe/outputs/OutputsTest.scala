package org.scalawag.jibe.outputs

import java.io.{File, PrintWriter}

import org.scalatest.FunSpec
import org.scalawag.jibe.FileUtils
import org.scalawag.jibe.outputs.MandateLibrary.InstallSoftware

import scala.concurrent.Await
import scala.concurrent.duration._

object OutputsTest extends FunSpec {
  // TODO: handle reporting/structure/metadata
  // TODO: make the implementation interface prettier

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

  def main(args: Array[String]): Unit = {
    implicit val rc = new RunContext

    val b = MandateLibrary.InstallSoftware.Shared
    val j = b.bind(UpstreamBoundMandate.fromLiteral(InstallSoftware.Input()))

    val pw = new PrintWriter(System.out)
    j.dump(pw)
    pw.flush()

    FileUtils.writeFileWithPrintWriter(new File("graph.dot"))(j.graph)

    println(Await.result(j.dryRunResult, Duration.Inf))
    println(Await.result(j.runResult, Duration.Inf))

    println(Await.result(j.runResult, Duration.Inf))
  }
}
