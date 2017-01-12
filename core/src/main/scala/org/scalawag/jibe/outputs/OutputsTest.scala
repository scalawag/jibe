package org.scalawag.jibe.outputs

import org.scalawag.timber.backend.receiver.ConsoleOutReceiver
import shapeless._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import Logging.log

object Logging {
  import org.scalawag.timber.api.{ImmediateMessage, Logger}
  import org.scalawag.timber.api.Level._
  import org.scalawag.timber.backend.DefaultDispatcher
  import org.scalawag.timber.backend.dispatcher.Dispatcher
  import org.scalawag.timber.backend.dispatcher.configuration.dsl._
  import org.scalawag.timber.backend.receiver.buffering.ImmediateFlushing
  import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter
  import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter.entry


  def initialize(): Unit = {
    implicit val MyEntryFormatter = new ProgrammableEntryFormatter(Seq(
      entry.timestamp
    ))

    val config = org.scalawag.timber.backend.dispatcher.configuration.Configuration {
      val out = new ConsoleOutReceiver(MyEntryFormatter) with ImmediateFlushing
      ( level >= DEBUG ) ~> out
    }

    val disp = new Dispatcher(config)
    DefaultDispatcher.set(disp)
  }

  val log = new Logger(tags = Set(ImmediateMessage))
}

object OutputsTest {

  trait Mandate[MI <: HList, MO <: HList] { me =>
    def dryRun(in: Future[Option[MI]]): Future[Option[MO]]
    def run(in: Future[MI]): Future[MO]

    def flatMap[YO <: HList](you: Mandate[MO, YO]) = new Mandate[MI, YO] {
      override def dryRun(in: Future[Option[MI]]): Future[Option[YO]] = you.dryRun(me.dryRun(in))
      override def run(in: Future[MI]): Future[YO] = you.run(me.run(in))
    }

    def join[YO <: HList](you: Mandate[MI, YO])(implicit prepend: Prepend[MO, YO]) = new Mandate[MI, Prepend[MO, YO]#Out] {

      override def dryRun(in: Future[Option[MI]]): Future[Option[Prepend[MO, YO]#Out]] = {
        val mof = me.dryRun(in)
        val yof = you.dryRun(in)

        mof flatMap {
          case None => Future.successful(None)
          case Some(mo) =>
            yof map {
              case None => None
              case Some(yo) => Some(mo ::: yo)
            }
        }
      }

      override def run(in: Future[MI]) = {
        val mof = me.run(in)
        val yof = you.run(in)

        mof flatMap { mo =>
          yof map { yo =>
            mo ::: yo
          }
        }
      }
    }
  }

  trait BaseMandate[I <: HList, O <: HList] extends Mandate[I, O] {
    protected[this] def dryRunLogic(in: I): Option[O]
    protected[this] def runLogic(in: I): O

    override def dryRun(foi: Future[Option[I]]) =
      foi map { oi =>
        oi flatMap { i =>
          dryRunLogic(i)
        }
      }

    override def run(fi: Future[I]) =
      fi map { i =>
        runLogic(i)
      }
  }

  class GenericMandate[I <: HList, O <: HList](name: String,
                                               dryRunDelay: FiniteDuration, dryRunLogicFn: I => Option[O],
                                               runDelay: FiniteDuration, runLogicFn: I => O)
    extends BaseMandate[I, O]
  {
    protected[this] def dryRunLogic(in: I): Option[O] = {
      log.debug(s"D S $name")
      if ( dryRunDelay > 0.milliseconds )
        Thread.sleep(dryRunDelay.toMillis)
      log.debug(s"D F $name")
      dryRunLogicFn(in)
    }

    protected[this] def runLogic(in: I): O = {
      log.debug(s"R S $name")
      if ( dryRunDelay > 0.milliseconds )
        Thread.sleep(runDelay.toMillis)
      log.debug(s"R F $name")
      runLogicFn(in)
    }
  }

  def main(args: Array[String]): Unit = {
    Logging.initialize()

    val a = new GenericMandate[HList, Int :: HNil]("a", 0 seconds, { _ => None }, 3 seconds, { _ => 8 :: HNil})
    val b = new GenericMandate[Int :: HNil, String :: HNil]("b", 0 seconds, { n => Some( ( "b" * n.head ) :: HNil ) }, 3 seconds, { n => ( "b" * n.head ) :: HNil })
    val m = a flatMap b

    println(Await.result(m.dryRun(Future.successful(Some(HNil))), Duration.Inf))

    println(Await.result(m.run(Future.successful(HNil)), Duration.Inf))

    val x = new GenericMandate[HList, Int :: HNil]("x", 100 millis, { _ =>  Some(6 :: HNil) }, 1 second, { _ => 6 :: HNil })
    val y = a join x

    println(Await.result(y.dryRun(Future.successful(Some(HNil))), Duration.Inf))

    println(Await.result(y.run(Future.successful(HNil)), Duration.Inf))
  }
}
