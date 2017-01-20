package org.scalawag.jibe.outputs


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
  import org.scalawag.timber.backend.receiver.ConsoleOutReceiver

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

  trait MandateFactory[IN, OUT] { me =>
    def create(in: MandateInput[IN])(implicit runContext: RunContext): Mandate[OUT]

    def flatMap[YOUT](you: MandateFactory[OUT, YOUT]) =
      new MandateFactory[IN, YOUT] {
        override def create(in: MandateInput[IN])(implicit runContext: RunContext) = you.create(me.create(in))
      }

    def join[YOUT](you: MandateFactory[IN, YOUT]) =
      new MandateFactory[IN, (OUT, YOUT)] {

        override def create(in: MandateInput[IN])(implicit runContext: RunContext) =
          new Mandate[(OUT, YOUT)] {
            private[this] val l: Mandate[OUT] = me.create(in)
            private[this] val r: Mandate[YOUT] = you.create(in)

            override protected[this] def dryRun()(implicit runContext: RunContext): Future[Option[(OUT, YOUT)]] =
              l.dryRunResults flatMap {
                case None => Future.successful(None)
                case Some(lo) =>
                  r.dryRunResults map {
                    case None => None
                    case Some(ro) => Some(lo, ro)
                  }
              }

            override protected[this] def run()(implicit runContext: RunContext): Future[(OUT, YOUT)] =
              l.runResults flatMap { lo =>
                r.runResults map { ro =>
                  (lo, ro)
                }
              }
          }
      }
  }

  abstract class Mandate[A](implicit runContext: RunContext) extends MandateInput[A] { me =>
    protected[this] def dryRun()(implicit runContext: RunContext): Future[Option[A]]
    protected[this] def run()(implicit runContext: RunContext): Future[A]

    lazy val dryRunResults: Future[Option[A]] = dryRun()
    lazy val runResults: Future[A] = run()


    def map[B](fn: A => B): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults.map(_.map(fn))

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults.map(fn)
      }

    def flatMap[B](fn: A => Future[B]): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults flatMap {
            case None => Future.successful(None)
            case Some(a) => fn(a).map(Some.apply)
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults.flatMap(fn)
      }

    def flatMap[B](you: Mandate[B]): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults flatMap {
            case None => Future.successful(None)
            case Some(a) => you.dryRunResults
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults.flatMap(_ => you.runResults)
      }

    def flatMap[B](you: MandateFactory[A, B]): Mandate[B] = you.create(me)

    def join[B](you: Mandate[B]) =
      new Mandate[(A, B)] {
        override def dryRun()(implicit runContext: RunContext): Future[Option[(A, B)]] = {
          // These need to be done outside of the callback structure below to ensure they happen in parallel.
          val mof = me.dryRunResults
          val yof = you.dryRunResults
          mof flatMap {
            case None => Future.successful(None)
            case Some(a) =>
              yof map {
                case None => None
                case Some(b) => Some(a, b)
              }
          }
        }

      override def run()(implicit runContext: RunContext) = {
        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val mof = me.runResults
        val yof = you.runResults
        mof flatMap { mo =>
          yof map { yo =>
            (mo, yo)
          }
        }
      }
    }
  }

  trait SimpleLogicMandateFactory[MI, MO] extends MandateFactory[MI, MO] {

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

  class GenericMandateFactory[I, O](name: String,
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

      object Factory extends SimpleLogicMandateFactory[Input, Unit] {

        override def create(in: MandateInput[Input])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug("WRF: see if file is already present")
              None
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug("WRF: sending the file")
              Thread.sleep(3000)
              log.debug("WRF: file sent")
            }
          }
      }

      def writeToRemoteFile(in: MandateInput[Input])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallAptKey {
      case class Input(keyserver: String, fingerprint: String)

      object Factory extends SimpleLogicMandateFactory[Input, Unit] {

        override def create(in: MandateInput[Input])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IAK: see if apt key ${in.fingerprint} is already installed")
              None
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IAK: install the apt key ${in.fingerprint}")
              Thread.sleep(3000)
              log.debug("IAK: installed key")
            }
          }
      }

      def installAptKey(in: MandateInput[Input])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object UpdateAptGet {
      case class Input(refreshInterval: Duration)

      object Factory extends SimpleLogicMandateFactory[Input, Unit] {

        override def create(in: MandateInput[Input])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"UAG: see if apt-get update has been run within the last ${in.refreshInterval}")
              None
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"UAG: run apt-get update")
              Thread.sleep(3000)
              log.debug("UAG: ran apt-get update")
            }
          }
      }

      def updateAptGet(in: MandateInput[Input])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallPackage {
      case class Input(name: String, version: Option[String] = None)
      case class Output(installedVersion: String)

      object Factory extends SimpleLogicMandateFactory[Input, Output] {

        override def create(in: MandateInput[Input])(implicit runContext: RunContext) =
          new SimpleLogicMandate(in) {
            override protected[this] def dryRunLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IP: see if package ${in} is installed")
              None
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IP: install package ${in}")
              Thread.sleep(3000)
              log.debug(s"IP: installed package $in")
              Output("3.2.3")
            }
          }
      }

      def installPackage(in: MandateInput[Input])(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallJava8 {
      case class Input(version: Option[String] = None)
      case class Output(version: String)

      object Factory extends MandateFactory[Input, Output] {

        override def create(in: MandateInput[Input])(implicit runContext: RunContext) = {
          val wrf =
            WriteRemoteFile.writeToRemoteFile(
              WriteRemoteFile.Input("/etc/apt/sources.list.d/webupd8team-java-trusty.list",
                                    "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main")
            )

          val iak =
            InstallAptKey.installAptKey(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B0889BF5709A105D03AC2518248EEA14886"))

          ( wrf join iak ) flatMap
            UpdateAptGet.updateAptGet(UpdateAptGet.Input(5 seconds)) flatMap
            InstallPackage.installPackage(InstallPackage.Input("oracle-java8-installer")) map { v =>
            Output(v.installedVersion)
          }
        }
      }

      def installJava8(in: MandateInput[Input] = Input())(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallSbt {
      case class Input(version: Option[String] = None)
      case class Output(version: String)

      object Factory extends MandateFactory[Input, Output] {

        override def create(in: MandateInput[Input])(implicit runContext: RunContext) = {
          val wrf =
            WriteRemoteFile.writeToRemoteFile(
              WriteRemoteFile.Input("/etc/apt/sources.list.d/webupd8team-java-trusty.list",
                "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main")
            )

          val iak =
            InstallAptKey.installAptKey(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B5889BF5709A105D03AC2518248EEA14886"))

          ( wrf join iak ) flatMap
            UpdateAptGet.updateAptGet(UpdateAptGet.Input(5 seconds)) flatMap
            InstallPackage.installPackage(InstallPackage.Input("sbt")) map { v =>
            Output(v.installedVersion)
          }
        }
      }

      def installSbt(in: MandateInput[Input] = Input())(implicit rc: RunContext) =
        Factory.create(in)
    }

    object InstallSoftware {
      def installSoftware()(implicit rc: RunContext) = {
        InstallJava8.installJava8() join InstallSbt.installSbt()
      }
    }
  }

  def main(args: Array[String]): Unit = {
    Logging.initialize()

    implicit val rc = new RunContext

    val seed = MandateInput.fromLiteral(())

    val af = new GenericMandateFactory[Unit, Int]("a", 0 seconds, { _ => None }, 3 seconds, { _ => 8})
    val a = af.create()
    val bf = new GenericMandateFactory[Int, String]("b", 0 seconds, { n => Some( ( "b" * n ) ) }, 3 seconds, { n => ( "b" * n ) })
    val m = bf.create(a)

    println(Await.result(m.dryRunResults, Duration.Inf))

    println(Await.result(m.runResults, Duration.Inf))

    val xf = new GenericMandateFactory[Unit, Int]("x", 100 millis, { _ =>  Some(6) }, 1 second, { _ => 6 })
    val x = xf.create(seed)

    val y = ( a join xf.create(seed) )

    println(Await.result(y.dryRunResults, Duration.Inf))

    println(Await.result(y.runResults, Duration.Inf))

    val j = MandateLibrary.InstallSoftware.installSoftware()
    println(Await.result(j.dryRunResults, Duration.Inf))
    println(Await.result(j.runResults, Duration.Inf))

    println(Await.result(j.runResults, Duration.Inf))
  }
}
