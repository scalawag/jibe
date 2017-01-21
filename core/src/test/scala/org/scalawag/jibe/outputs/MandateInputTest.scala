package org.scalawag.jibe.outputs

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class MandateInputTest extends FunSpec with Matchers with MockFactory {
  implicit val runContext = new RunContext

  describe("join dry-run combinations") {

    def testDryRunResultCombination[A](ar: Try[DryRun.Result[A]], br: Try[DryRun.Result[A]], jr: Try[DryRun.Result[A]]) =
      it(s"should return $jr if the inputs are $ar and $br") {

        class TestMandateInput(r: Try[DryRun.Result[A]]) extends MandateInput[A] {
          var callCount = 0

          override def dryRunResult = {
            callCount += 1
            r match {
              case Success(x) => Future.successful(x)
              case Failure(x) => Future.failed(x)
            }
          }

          override def runResult = ???
        }


        val a = new TestMandateInput(ar)
        val b = new TestMandateInput(br)
        val m = a join b

        Await.ready(m.dryRunResult, Duration.Inf)
        val o = m.dryRunResult.value.get

        o shouldBe jr

        a.callCount shouldBe 1
        b.callCount shouldBe 1
      }

    val fa = Failure(new RuntimeException("a"))
    val fb = Failure(new RuntimeException("b"))
    val bl = Success(DryRun.Blocked)
    val ne = Success(DryRun.Needed)
    val ua = Success(DryRun.Unneeded("a"))
    val ub = Success(DryRun.Unneeded("b"))
    val uj = Success(DryRun.Unneeded("a", "b"))

    testDryRunResultCombination(fa, fb, bl)
    testDryRunResultCombination(fa, bl, bl)
    testDryRunResultCombination(fa, ne, bl)
    testDryRunResultCombination(fa, ub, bl)

    testDryRunResultCombination(bl, fb, bl)
    testDryRunResultCombination(bl, bl, bl)
    testDryRunResultCombination(bl, ne, bl)
    testDryRunResultCombination(bl, ub, bl)

    testDryRunResultCombination(ne, fb, bl)
    testDryRunResultCombination(ne, bl, bl)
    testDryRunResultCombination(ne, ne, ne)
    testDryRunResultCombination(ne, ub, ne)

    testDryRunResultCombination(ua, fb, bl)
    testDryRunResultCombination(ua, bl, bl)
    testDryRunResultCombination(ua, ne, ne)
    testDryRunResultCombination(ua, ub, uj)

  }

  describe("join run combinations") {

    def testRunResultCombination[A](ar: Try[Run.Result[A]], br: Try[Run.Result[A]], jr: Try[Run.Result[A]]) =
      it(s"should return $jr if the inputs are $ar and $br") {

        class TestMandateInput(r: Try[Run.Result[A]]) extends MandateInput[A] {
          var callCount = 0

          override def dryRunResult = ???
          override def runResult = {
            callCount += 1
            r match {
              case Success(x) => Future.successful(x)
              case Failure(x) => Future.failed(x)
            }
          }
        }


        val a = new TestMandateInput(ar)
        val b = new TestMandateInput(br)
        val m = a join b

        Await.ready(m.runResult, Duration.Inf)
        val o = m.runResult.value.get

        o shouldBe jr

        a.callCount shouldBe 1
        b.callCount shouldBe 1
      }

    val fa = Failure(new RuntimeException("a"))
    val fb = Failure(new RuntimeException("b"))
    val bl = Success(Run.Blocked)
    val da = Success(Run.Done("a"))
    val db = Success(Run.Done("b"))
    val dj = Success(Run.Done("a", "b"))
    val ua = Success(Run.Unneeded("a"))
    val ub = Success(Run.Unneeded("b"))
    val uj = Success(Run.Unneeded("a", "b"))

    testRunResultCombination(fa, fb, bl)
    testRunResultCombination(fa, bl, bl)
    testRunResultCombination(fa, db, bl)
    testRunResultCombination(fa, ub, bl)

    testRunResultCombination(bl, fb, bl)
    testRunResultCombination(bl, bl, bl)
    testRunResultCombination(bl, db, bl)
    testRunResultCombination(bl, ub, bl)

    testRunResultCombination(da, fb, bl)
    testRunResultCombination(da, bl, bl)
    testRunResultCombination(da, db, dj)
    testRunResultCombination(da, ub, dj)

    testRunResultCombination(ua, fb, bl)
    testRunResultCombination(ua, bl, bl)
    testRunResultCombination(ua, db, dj)
    testRunResultCombination(ua, ub, uj)

  }

  describe("join timing") {

    it("should calculate the two dry-run results in parallel") {
      val om = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 1 second, 1 second)
      val a = om.bind(())
      val b = om.bind(())
      val m = a join b
      val o = Await.result(m.dryRunResult, Duration.Inf)

      o shouldBe DryRun.Needed

      a.dryRunStart should be < a.dryRunFinish
      b.dryRunStart should be < b.dryRunFinish

      a.dryRunStart should be <= b.dryRunFinish
      b.dryRunStart should be <= a.dryRunFinish

      a.runStart shouldBe None
      b.runStart shouldBe None
      a.runFinish shouldBe None
      b.runFinish shouldBe None
    }

    it("should calculate the two run results in parallel") {
      val om = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 1 second, 1 second)
      val a = om.bind(())
      val b = om.bind(())
      val m = a join b
      val o = Await.result(m.runResult, Duration.Inf)

      o shouldBe Run.Done(("a", "a"))

      a.runStart should be < a.runFinish
      b.runStart should be < b.runFinish

      a.runStart should be <= b.runFinish
      b.runStart should be <= a.runFinish

      a.dryRunStart shouldBe None
      b.dryRunStart shouldBe None
      a.dryRunFinish shouldBe None
      b.dryRunFinish shouldBe None
    }

  }
}
