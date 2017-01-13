package org.scalawag.jibe.outputs

import org.scalawag.jibe.backend.Commander
import org.scalawag.timber.backend.receiver.ConsoleOutReceiver
import shapeless._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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
  // TODO: handle reporting/structure/metadata
  // TODO: make the implementation interface prettier
  // TODO: maybe hide HLists as argument lists/tuples

  class MandateReport(val description: String) {

  }

  // Mandate input should be defined when it's created so that it's output can only be calculated once without fear that
  // the inputs will change.  If a mandate allows multiple calls (with different arguments), then its output can't be
  // cached as the canonical output of that mandate.

  class RunContext {
    val log = Logging.log
//    var roots: MandateReport = ???
  }

  trait MandateInput[+A] {
    def dryRunResults: Future[Option[A]]
    def runResults: Future[A]
  }

  object MandateInput {
    implicit def fromLiteral[A](a: A) = new MandateInput[A] {
      override val dryRunResults = Future.successful(Some(a))
      override val runResults = Future.successful(a)
    }
  }

  trait MandateFactory[IN <: HList, OUT <: HList] { me =>
    def create(in: MandateInput[IN])(implicit runContext: RunContext): Mandate[OUT]

    def flatMap[YOUT <: HList](you: MandateFactory[OUT, YOUT]) =
      new MandateFactory[IN, YOUT] {
        override def create(in: MandateInput[IN])(implicit runContext: RunContext) = you.create(me.create(in))
      }

    def join[YOUT <: HList](you: MandateFactory[IN, YOUT])(implicit prepend: Prepend[OUT, YOUT]) =
      new MandateFactory[IN, Prepend[OUT, YOUT]#Out] {

        override def create(in: MandateInput[IN])(implicit runContext: RunContext) =
          new Mandate[Prepend[OUT, YOUT]#Out] {
            private[this] val l: Mandate[OUT] = me.create(in)
            private[this] val r: Mandate[YOUT] = you.create(in)

            override protected[this] def dryRun()(implicit runContext: RunContext): Future[Option[Prepend[OUT, YOUT]#Out]] =
              l.dryRunResults flatMap {
                case None => Future.successful(None)
                case Some(lo) =>
                  r.dryRunResults map {
                    case None => None
                    case Some(ro) => Some(lo ::: ro)
                  }
              }

            override protected[this] def run()(implicit runContext: RunContext): Future[Prepend[OUT, YOUT]#Out] =
              l.runResults flatMap { lo =>
                r.runResults map { ro =>
                  lo ::: ro
                }
              }
          }
      }
  }

  abstract class Mandate[MO <: HList](implicit runContext: RunContext) extends MandateInput[MO] { me =>
    protected[this] def dryRun()(implicit runContext: RunContext): Future[Option[MO]]
    protected[this] def run()(implicit runContext: RunContext): Future[MO]

    lazy val dryRunResults: Future[Option[MO]] = dryRun()
    lazy val runResults: Future[MO] = run()

/*
    private[this] val dryRunResults = new AtomicReference[Option[Future[Option[MO]]]](None)
    private[this] val runResults = new AtomicReference[Option[Future[MO]]](None)

    def dryRunResults(implicit runContext: RunContext): Future[Option[MO]] = {
      val p = Promise[Option[MO]]
      if ( dryRunResults.compareAndSet(None, Some(p.future)) ) {
        // Kick off the work and make this promise eventually hold the correct value
        p.completeWith(dryRun())
        p.future
      } else {
        dryRunResults.get.get
      }
    }

    def runResults(implicit runContext: RunContext): Future[MO] = {
      val p = Promise[MO]
      if ( runResults.compareAndSet(None, Some(p.future)) ) {
        // Kick off the work and make this promise eventually hold the correct value
        p.completeWith(run())
        p.future
      } else {
        runResults.get.get
      }
    }
*/

    def join[YO <: HList](you: Mandate[YO])(implicit prepend: Prepend[MO, YO]) =
      new Mandate[Prepend[MO, YO]#Out] {
        override def dryRun()(implicit runContext: RunContext): Future[Option[Prepend[MO, YO]#Out]] = {
          me.dryRunResults flatMap {
            case None => Future.successful(None)
            case Some(mo) =>
              you.dryRunResults map {
                case None => None
                case Some(yo) => Some(mo ::: yo)
              }
          }
        }

      override def run()(implicit runContext: RunContext) = {
        me.runResults flatMap { mo =>
          you.runResults map { yo =>
            mo ::: yo
          }
        }
      }
    }
  }

  trait SimpleLogicMandateFactory[MI <: HList, MO <: HList] extends MandateFactory[MI, MO] {

    abstract class SimpleLogicMandate(upstream: MandateInput[MI])(implicit runContext: RunContext) extends Mandate[MO] {
      protected[this] def dryRunLogic(in: MI)(implicit runContext: RunContext): Option[MO]
      protected[this] def runLogic(in: MI)(implicit runContext: RunContext): MO

      override protected[this] def dryRun()(implicit runContext: RunContext): Future[Option[MO]] =
        upstream.dryRunResults map { oi =>
          oi flatMap { i =>
            dryRunLogic(i)
          }
        }

      override protected[this] def run()(implicit runContext: RunContext): Future[MO] =
        upstream.runResults map { i =>
          runLogic(i)
        }
    }

  }

  class GenericMandateFactory[I <: HList, O <: HList](name: String,
                                                      dryRunDelay: FiniteDuration, dryRunLogicFn: I => Option[O],
                                                      runDelay: FiniteDuration, runLogicFn: I => O)
    extends SimpleLogicMandateFactory[I, O]
  {

    class GenericMandate(upstream: MandateInput[I])(implicit val runContext: RunContext)
      extends SimpleLogicMandate(upstream)(runContext)
    {

      override protected[this] def dryRunLogic(in: I)(implicit runContext: RunContext) = {
        import runContext._

        log.debug(s"D S $name")
        if ( dryRunDelay > 0.milliseconds )
          Thread.sleep(dryRunDelay.toMillis)
        log.debug(s"D F $name")
        dryRunLogicFn(in)
      }

      override protected[this] def runLogic(in: I)(implicit runContext: RunContext) = {
        import runContext._

        log.debug(s"R S $name")
        if ( runDelay > 0.milliseconds )
          Thread.sleep(runDelay.toMillis)
        log.debug(s"R F $name")
        runLogicFn(in)
      }
    }

    override def create(in: MandateInput[I])(implicit runContext: RunContext) = new GenericMandate(in)
  }

  object MandateLibrary {

    object WriteRemoteFile {
      case class Input(path: String, content: String)

      object Factory extends SimpleLogicMandateFactory[Input :: HNil, HNil] {

        override def create(in: MandateInput[Input :: HNil])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug("WRF: see if file is already present")
              None
            }

            override protected[this] def runLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug("WRF: send the file")
              HNil
            }

          }
      }

      def writeToRemoteFile(in: MandateInput[Input :: HNil])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallAptKey {
      case class Input(keyserver: String, fingerprint: String)

      object Factory extends SimpleLogicMandateFactory[Input :: HNil, HNil] {

        override def create(in: MandateInput[Input :: HNil])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IAK: see if apt key ${in.head.fingerprint} is already installed")
              None
            }

            override protected[this] def runLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IAK: install the apt key ${in.head.fingerprint}")
              HNil
            }

          }
      }

      def installAptKey(in: MandateInput[Input :: HNil])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object UpdateAptGet {
      case class Input(refreshInterval: Duration)

      object Factory extends SimpleLogicMandateFactory[Input :: HNil, HNil] {

        override def create(in: MandateInput[Input :: HNil])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"UAG: see if apt-get update has been run within the last ${in.head.refreshInterval}")
              None
            }

            override protected[this] def runLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"UAG: run apt-get update")
              HNil
            }
          }
      }

      def updateAptGet(in: MandateInput[Input :: HNil])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallPackage {
      case class Input(name: String, version: Option[String] = None)
      case class Output(installedVersion: String)

      object Factory extends SimpleLogicMandateFactory[Input :: HNil, Output :: HNil] {

        override def create(in: MandateInput[Input :: HNil])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IP: see if package ${in.head} is installed")
              None
            }

            override protected[this] def runLogic(in: Input :: HNil)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IP: install package ${in.head}")
              Output("3.2.3") :: HNil
            }
          }
      }

      def installPackage(in: MandateInput[Input :: HNil])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallJava8 {
      case class Input(version: Option[String])
      case class Output(version: String)

      object Factory extends MandateFactory[Input :: HNil, Output :: HNil] {

        override def create(in: MandateInput[HNil])(implicit runContext: RunContext) = {
          val wrf =
            WriteRemoteFile.writeToRemoteFile(
              WriteRemoteFile.Input("/etc/apt/sources.list.d/webupd8team-java-trusty.list",
                                    "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main") :: HNil
            )

          val iak =
            InstallAptKey.installAptKey(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B0889BF5709A105D03AC2518248EEA14886") :: HNil)

          ( wrf join iak )

          val uag =
            UpdateAptGet.updateAptGet(UpdateAptGet.Input(5 seconds) :: HNil)

          val ip =
            InstallPackage.installPackage(InstallPackage.Input("oracle-java8-installer") :: HNil)

        }
      }

      def installJava8(in: MandateInput[Input :: HNil])(implicit rc: RunContext) =
        Factory.create(in)
    }
  }

  def main(args: Array[String]): Unit = {
    Logging.initialize()

    implicit val rc = new RunContext

    val seed = MandateInput.fromLiteral(HNil)

    val af = new GenericMandateFactory[HList, Int :: HNil]("a", 0 seconds, { _ => None }, 3 seconds, { _ => 8 :: HNil})
    val a = af.create(HNil)
    val bf = new GenericMandateFactory[Int :: HNil, String :: HNil]("b", 0 seconds, { n => Some( ( "b" * n.head ) :: HNil ) }, 3 seconds, { n => ( "b" * n.head ) :: HNil })
    val m = bf.create(a)

    println(Await.result(m.dryRunResults, Duration.Inf))

    println(Await.result(m.runResults, Duration.Inf))

    val xf = new GenericMandateFactory[HList, Int :: HNil]("x", 100 millis, { _ =>  Some(6 :: HNil) }, 1 second, { _ => 6 :: HNil })
    val x = xf.create(seed)

    val y = ( a join xf.create(seed) )

    println(Await.result(y.dryRunResults, Duration.Inf))

    println(Await.result(y.runResults, Duration.Inf))
  }
}
