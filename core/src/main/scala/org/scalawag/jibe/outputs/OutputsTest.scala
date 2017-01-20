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

  sealed trait MandateResults[+A]
  trait DryRunResults[+A] extends MandateResults[A]
  trait RunResults[+A] extends MandateResults[A]

  trait Completed[+A] extends MandateResults[A] {
    val result: A
  }

  case object Needed extends DryRunResults[Nothing]
  case class Unneeded[A](override val result: A) extends DryRunResults[A] with RunResults[A] with Completed[A]

  case class Done[A](override val result: A) extends RunResults[A] with Completed[A]

//  case object Skipped extends DryRunResults[Nothing] with RunResults[Nothing] // TODO: this may be replaced by absence in the report
  case object Blocked extends DryRunResults[Nothing] with RunResults[Nothing]

  trait MandateInput[+A] {
    def dryRunResults: Future[DryRunResults[A]]
    def runResults: Future[RunResults[A]]
  }

  object MandateInput {
    implicit def fromLiteral[A](a: A) = new MandateInput[A] {
      override val dryRunResults = Future.successful(Unneeded(a))
      override val runResults = Future.successful(Unneeded(a))
    }
  }

  trait MandateFactory[IN, OUT] { me =>
    def create(in: MandateInput[IN])(implicit runContext: RunContext): Mandate[OUT]

    def flatMap[YOUT](you: MandateFactory[OUT, YOUT]) =
      new MandateFactory[IN, YOUT] {
        override def create(in: MandateInput[IN])(implicit runContext: RunContext) =
          you.create(me.create(in))
      }

    def join[YOUT](you: MandateFactory[IN, YOUT]) =
      new MandateFactory[IN, (OUT, YOUT)] {
        override def create(in: MandateInput[IN])(implicit runContext: RunContext) =
          me.create(in) join you.create(in)
      }
  }

  abstract class Mandate[A](implicit runContext: RunContext) extends MandateInput[A] { me =>
    protected[this] def dryRun()(implicit runContext: RunContext): Future[DryRunResults[A]]
    protected[this] def run()(implicit runContext: RunContext): Future[RunResults[A]]

    lazy val dryRunResults: Future[DryRunResults[A]] = dryRun()
    lazy val runResults: Future[RunResults[A]] = run()

    def map[B](fn: A => B): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults map {
            case Unneeded(r) => Unneeded(fn(r))
            case Needed => Needed
            case Blocked => Blocked
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults map {
            case Done(r) => Done(fn(r))
            case Unneeded(r) => Unneeded(fn(r))
            case Blocked => Blocked
          }
      }

    def flatMap[B](fn: A => Future[B]): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults flatMap {
            case Unneeded(r) => fn(r).map(Unneeded.apply)
            case Needed => Future.successful(Needed)
            case Blocked => Future.successful(Blocked)
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults flatMap {
            case Unneeded(r) => fn(r).map(Unneeded.apply)
            case Done(r) => fn(r).map(Done.apply)
            case Blocked => Future.successful(Blocked)
          }
      }

    def flatMap[B](you: Mandate[B]): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults flatMap {
            case Unneeded(r) => you.dryRunResults
            case Needed => Future.successful(Needed)
            case Blocked => Future.successful(Blocked)
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults flatMap {
            case Unneeded(mr) => you.runResults
            case Done(yr) =>
              // We need to return done if this returned Done to indicate that an action was taken.
              you.runResults map {
                case Done(yr) => Done(yr)
                case Unneeded(yr) => Done(yr)
                case Blocked => Blocked
              }
            case Blocked => Future.successful(Blocked)
          }
      }

    def flatMap[B](you: MandateFactory[A, B]): Mandate[B] = you.create(me)

    def join[B](you: Mandate[B]) =
      new Mandate[(A, B)] {
        override def dryRun()(implicit runContext: RunContext): Future[DryRunResults[(A, B)]] = {
          // These need to be done outside of the callback structure below to ensure they happen in parallel.
          val lf = me.dryRunResults
          val rf = you.dryRunResults
          lf flatMap {
            case Unneeded(lo) =>
              rf map {
                case Unneeded(ro) => Unneeded(lo, ro)
                case Blocked => Blocked
                case Needed => Needed
              }
            case Blocked => Future.successful(Blocked)
            case Needed => Future.successful(Needed)
          }
        }

      override def run()(implicit runContext: RunContext) = {
        // These need to be done outside of the callback structure below to ensure they happen in parallel.
        val lf = me.runResults
        val rf = you.runResults
        lf flatMap {
          case Unneeded(lo) =>
            rf map {
              case Unneeded(ro) => Unneeded(lo, ro)
              case Done(ro) => Done(lo, ro)
              case Blocked => Blocked
            }
          case Done(lo) =>
            rf map {
              case Unneeded(ro) => Done(lo, ro)
              case Done(ro) => Done(lo, ro)
              case Blocked => Blocked
            }
          case Blocked => Future.successful(Blocked)
        }
      }
    }

  }

  trait SimpleLogicMandateFactory[MI, MO] extends MandateFactory[MI, MO] {

    abstract class SimpleLogicMandate(upstream: MandateInput[MI])(implicit runContext: RunContext) extends Mandate[MO] {
      protected[this] def dryRunLogic(in: MI)(implicit runContext: RunContext): DryRunResults[MO]
      protected[this] def runLogic(in: MI)(implicit runContext: RunContext): RunResults[MO]

      override protected[this] def dryRun()(implicit runContext: RunContext) =
        upstream.dryRunResults flatMap {
          case c: Completed[MI] => Future(dryRunLogic(c.result))
          case Blocked => Future.successful(Blocked)
          case Needed => Future.successful(Needed)
        }

      override protected[this] def run()(implicit runContext: RunContext) =
        upstream.runResults flatMap {
          case c: Completed[MI] => Future(runLogic(c.result))
          case Blocked => Future.successful(Blocked)
        }
    }

  }

  class GenericMandateFactory[I, O](name: String,
                                    dryRunDelay: FiniteDuration, dryRunLogicFn: I => DryRunResults[O],
                                    runDelay: FiniteDuration, runLogicFn: I => RunResults[O])
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
              Needed
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug("WRF: sending the file")
              Thread.sleep(3000)
              log.debug("WRF: file sent")
              Done(())
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
              Needed
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IAK: install the apt key ${in.fingerprint}")
              Thread.sleep(3000)
              log.debug("IAK: installed key")
              Done(())
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
              Needed
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"UAG: run apt-get update")
              Thread.sleep(3000)
              log.debug("UAG: ran apt-get update")
              Done(())
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
              Needed
            }

            override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
              import runContext._
              log.debug(s"IP: install package ${in}")
              Thread.sleep(3000)
              log.debug(s"IP: installed package $in")
              Done(Output("3.2.3"))
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

    val af = new GenericMandateFactory[Unit, Int]("a", 0 seconds, { _ => Needed }, 3 seconds, { _ => Done(8)})
    val a = af.create(())
    val bf = new GenericMandateFactory[Int, String]("b", 0 seconds, { n => Unneeded( ( "b" * n ) ) }, 3 seconds, { n => Done( "b" * n ) })
    val m = bf.create(a)

    println(Await.result(m.dryRunResults, Duration.Inf))

    println(Await.result(m.runResults, Duration.Inf))

    val xf = new GenericMandateFactory[Unit, Int]("x", 100 millis, { _ =>  Unneeded(6) }, 1 second, { _ => Done(6) })
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
