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

        class TestMandateInput(r: Try[DryRun.Result[A]]) extends UpstreamBoundMandate[A] {
          var callCount = 0

          override val upstreams: Iterable[UpstreamBoundMandate[_]] = Iterable.empty

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
//        val m = a join b
//
//        Await.ready(m.dryRunResult, Duration.Inf)
//        val o = m.dryRunResult.value.get
//
//        o shouldBe jr

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
/*
  describe("join run combinations") {

    def testRunResultCombination[A](ar: Try[Run.Result[A]], br: Try[Run.Result[A]], jr: Try[Run.Result[A]]) =
      it(s"should return $jr if the inputs are $ar and $br") {

        class TestMandateInput(r: Try[Run.Result[A]]) extends UpstreamBoundMandate[A] {
          var callCount = 0

          override def dryRunResult = Future.successful(DryRun.Needed)

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

  describe("join concurrency") {

    it("should calculate the two dry-run results in parallel") {
      val oa = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 1 second, 1 second)
      val ob = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 1 second, 1 second)
      val om = oa map { _ => () } flatMap ob
      val m = om.bind(())
      val o = Await.result(m.dryRunResult, Duration.Inf)

      o shouldBe DryRun.Needed

      oa.dryRunStart should be < oa.dryRunFinish
      ob.dryRunStart should be < ob.dryRunFinish

      oa.dryRunStart should be <= ob.dryRunFinish
      ob.dryRunStart should be <= oa.dryRunFinish

      oa.runStart shouldBe None
      ob.runStart shouldBe None
      oa.runFinish shouldBe None
      ob.runFinish shouldBe None
    }

    it("should calculate the two run results in parallel") {
      val oa = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 500 millis, 1 second)
      val ob = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 500 millis, 1 second)
      val om = oa map { _ => () } flatMap ob
      val m = om.bind(())
      val o = Await.result(m.runResult, Duration.Inf)

      o shouldBe Run.Done(("a", "a"))

      oa.dryRunStart should be < oa.dryRunFinish
      oa.dryRunFinish should be <= oa.runStart

      ob.dryRunStart should be < ob.dryRunFinish
      ob.dryRunFinish should be <= ob.runStart

      oa.runStart should be < oa.runFinish
      ob.runStart should be < ob.runFinish

      oa.runStart should be <= ob.runFinish
      ob.runStart should be <= oa.runFinish
    }

  }

  describe("flatMap dry-run combinations") {

    def testDryRunResultCombination[A](ar: Try[DryRun.Result[A]], br: Try[DryRun.Result[A]], jr: Try[DryRun.Result[A]]) =
      it(s"should return $jr if the inputs are $ar and $br") {

        class TestOpenMandate(r: Try[DryRun.Result[A]]) extends Mandate[Unit, A] {
          var callCount = 0

          override val upstream = Iterable.empty

          class TestMandate(r: Try[DryRun.Result[A]]) extends UpstreamBoundMandate[A] {
            override def dryRunResult = {
              callCount += 1
              r match {
                case Success(x) => Future.successful(x)
                case Failure(x) => Future.failed(x)
              }
            }

            override def runResult = ???
          }

          override def bind(in: UpstreamBoundMandate[Unit])(implicit runContext: RunContext) = new TestMandate(r)
        }

        val oa = new TestOpenMandate(ar)
        val ob = new TestOpenMandate(br)
        val om = oa map { _ => () } flatMap ob
        val m = om.bind(())

        Await.ready(m.dryRunResult, Duration.Inf)
        val o = m.dryRunResult.value.get

        o shouldBe jr

        oa.callCount shouldBe 1
        ob.callCount shouldBe 1

      }


    val fa = Failure(new RuntimeException("a"))
    val fb = Failure(new RuntimeException("b"))
    val bl = Success(DryRun.Blocked)
    val ne = Success(DryRun.Needed)
    val ua = Success(DryRun.Unneeded("a"))
    val ub = Success(DryRun.Unneeded("b"))

    testDryRunResultCombination(fa, fb, fb)
    testDryRunResultCombination(fa, bl, bl)
    testDryRunResultCombination(fa, ne, ne)
    testDryRunResultCombination(fa, ub, ub)

    testDryRunResultCombination(bl, fb, fb)
    testDryRunResultCombination(bl, bl, bl)
    testDryRunResultCombination(bl, ne, ne)
    testDryRunResultCombination(bl, ub, ub)

    testDryRunResultCombination(ne, fb, fb)
    testDryRunResultCombination(ne, bl, bl)
    testDryRunResultCombination(ne, ne, ne)
    testDryRunResultCombination(ne, ub, ub)

    testDryRunResultCombination(ua, fb, fb)
    testDryRunResultCombination(ua, bl, bl)
    testDryRunResultCombination(ua, ne, ne)
    testDryRunResultCombination(ua, ub, ub)
  }

  describe("flatMap run combinations") {

    def testRunResultCombination[A](ar: Try[Run.Result[A]], br: Try[Run.Result[A]], jr: Try[Run.Result[A]], bCallCount: Int) =
      it(s"should return $jr if the inputs are $ar and $br") {

        class TestOpenMandate(r: Try[Run.Result[A]]) extends Mandate[Unit, A] {
          var callCount = 0

          override val upstream = Iterable.empty

          class TestMandateInput(r: Try[Run.Result[A]]) extends UpstreamBoundMandate[A] {

            override def dryRunResult = Future.successful(DryRun.Needed)

            override def runResult =  {
              callCount += 1
              r match {
                case Success(x) => Future.successful(x)
                case Failure(x) => Future.failed(x)
              }
            }
          }

          override def bind(in: UpstreamBoundMandate[Unit])(implicit runContext: RunContext) = new TestMandateInput(r)
        }

        val oa = new TestOpenMandate(ar)
        val ob = new TestOpenMandate(br)
        val om = oa map { _ => () } flatMap ob
        val m = om.bind(())

        Await.ready(m.runResult, Duration.Inf)
        val o = m.runResult.value.get

        o shouldBe jr

        oa.callCount shouldBe 1
        ob.callCount shouldBe bCallCount
      }

    val fa = Failure(new RuntimeException("a"))
    val fb = Failure(new RuntimeException("b"))
    val bl = Success(Run.Blocked)
    val da = Success(Run.Done("a"))
    val db = Success(Run.Done("b"))
    val ua = Success(Run.Unneeded("a"))
    val ub = Success(Run.Unneeded("b"))

    testRunResultCombination(fa, fb, bl, 0)
    testRunResultCombination(fa, bl, bl, 0)
    testRunResultCombination(fa, db, bl, 0)
    testRunResultCombination(fa, ub, bl, 0)

    testRunResultCombination(bl, fb, bl, 0)
    testRunResultCombination(bl, bl, bl, 0)
    testRunResultCombination(bl, db, bl, 0)
    testRunResultCombination(bl, ub, bl, 0)

    testRunResultCombination(da, fb, bl, 1)
    testRunResultCombination(da, bl, bl, 1)
    testRunResultCombination(da, db, db, 1)
    testRunResultCombination(da, ub, db, 1)

    testRunResultCombination(ua, fb, bl, 1)
    testRunResultCombination(ua, bl, bl, 1)
    testRunResultCombination(ua, db, db, 1)
    testRunResultCombination(ua, ub, ub, 1)
  }

  describe("flatMap concurrency") {

    it("should calculate the two dry-run results in parallel") {
      val oa = new GenericOpenMandate[Unit, String]({ _ => Some("a") }, { _ => "a" }, 1 second, 1 second)
      val ob = new GenericOpenMandate[Unit, String]({ _ => Some("a") }, { _ => "a" }, 1 second, 1 second)
      val om = oa map { _ => () } flatMap ob
      val m = om.bind(())
      val o = Await.result(m.dryRunResult, Duration.Inf)

      o shouldBe DryRun.Unneeded("a")

      oa.dryRunStart should be < oa.dryRunFinish
      ob.dryRunStart should be < ob.dryRunFinish

      oa.dryRunStart should be <= ob.dryRunFinish
      ob.dryRunStart should be <= oa.dryRunFinish

      oa.runStart shouldBe None
      ob.runStart shouldBe None
      oa.runFinish shouldBe None
      ob.runFinish shouldBe None
    }

    it("should calculate the two run results in series") {
      val oa = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 1 second, 1 second)
      val ob = new GenericOpenMandate[Unit, String]({ _ => None }, { _ => "a" }, 1 second, 1 second)
      val om = oa map { _ => () } flatMap ob
      val m = om.bind(())
      val o = Await.result(m.runResult, Duration.Inf)

      o shouldBe Run.Done("a")

      oa.dryRunStart should be < oa.dryRunFinish
      ob.dryRunStart should be < ob.dryRunFinish
      oa.runStart should be < oa.runFinish
      ob.runStart should be < ob.runFinish

      oa.dryRunFinish should be <= oa.runStart
      ob.dryRunFinish should be <= oa.runStart
      oa.runFinish should be <= ob.runStart
    }

  }
*/
}
