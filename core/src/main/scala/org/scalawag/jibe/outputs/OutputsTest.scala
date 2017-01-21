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

  sealed trait MandateResults[+A] {
    type ResultsType <: MandateResults[A]
  }

  trait DryRunResults[+A] extends MandateResults[A] {
    def map[B](fn: A => B): DryRunResults[B]
    def flatMap[B](fn: A => DryRunResults[B]): DryRunResults[B]
  }

  trait RunResults[+A] extends MandateResults[A] {
    def map[B](fn: A => B): RunResults[B]
    def flatMap[B](fn: A => RunResults[B]): DryRunResults[B]
  }

  trait Completed[+A] extends MandateResults[A] {
    val result: A

    def map[B](fn: A => B): DryRunResults[B] = this match {
      case Unneeded(r) => Unneeded(fn(r))
      case Done(r) => Done(fn(r))
    }
    def flatMap[B](fn: A => DryRunResults[B]): DryRunResults[B] = fn(result)
  }

  case object Needed extends DryRunResults[Nothing] {
    override def map[B](fn: (Nothing) => B) = Needed
    override def flatMap[B](fn: (Nothing) => DryRunResults[B]) = Needed
  }

  case class Unneeded[A](override val result: A) extends DryRunResults[A] with RunResults[A] with Completed[A] {
    override def map[B](fn: A => B): DryRunResults[B] = Unneeded(fn(result))
    override def flatMap[B](fn: A => DryRunResults[B]): DryRunResults[B] = fn(result)
  }

  case class Done[A](override val result: A) extends RunResults[A] with Completed[A] {
    override def map[B](fn: A => B): RunResults[B] = Done(fn(result))
    override def flatMap[B](fn: A => RunResults[B]): RunResults[B] = fn(result)
  }

//  case object Skipped extends DryRunResults[Nothing] with RunResults[Nothing] // TODO: this may be replaced by absence in the report
  case object Blocked extends DryRunResults[Nothing] with RunResults[Nothing] {
    override def map[B](fn: Nothing => B): RunResults[B] = Blocked
    override def flatMap[B](fn: (Nothing) => DryRunResults[B]) = Blocked
  }

  trait MandateInput[+A] { me =>
    def dryRunResults: Future[DryRunResults[A]]
    def runResults: Future[RunResults[A]]

    def map[B](fn: A => B)(implicit runContext: RunContext): Mandate[B] =
      new Mandate[B] {
        override protected[this] def dryRun()(implicit runContext: RunContext) =
          me.dryRunResults map { drr =>
            drr.map(fn)
          }

        override protected[this] def run()(implicit runContext: RunContext) =
          me.runResults map { rr =>
            rr.map(fn)
          }
      }

    /* TODO: consider taking this out if it's not useful
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
    */

    def flatMap[B](you: Mandate[B])(implicit runContext: RunContext): Mandate[B] =
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
            case Done(mr) =>
              // We need to return done if this returned Done to indicate that an action was taken.
              you.runResults map {
                case Done(yr) => Done(yr)
                case Unneeded(yr) => Done(yr)
                case Blocked => Blocked
              }
            case Blocked => Future.successful(Blocked)
          }
      }

    def flatMap[C >: A, B](you: OpenMandate[C, B])(implicit runContext: RunContext): Mandate[B] = you.bind(me)

    def join[C >: A, B](you: Mandate[B])(implicit runContext: RunContext): Mandate[(C,B)] =
      new Mandate[(C, B)] {
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

  object MandateInput {
    implicit def fromLiteral[A](a: A): MandateInput[A] = new MandateInput[A] {
      override val dryRunResults = Future.successful(Unneeded(a))
      override val runResults = Future.successful(Unneeded(a))
    }
  }

  trait OpenMandate[IN, OUT] { me =>
    def bind(in: MandateInput[IN])(implicit runContext: RunContext): Mandate[OUT]

    def flatMap[YOUT](you: OpenMandate[OUT, YOUT]) =
      new OpenMandate[IN, YOUT] {
        override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
          you.bind(me.bind(in))
      }

    def join[YOUT](you: OpenMandate[IN, YOUT]) =
      new OpenMandate[IN, (OUT, YOUT)] {
        override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
          me.bind(in) join you.bind(in)
      }
  }

  abstract class Mandate[A](implicit runContext: RunContext) extends MandateInput[A] { me =>
    protected[this] def dryRun()(implicit runContext: RunContext): Future[DryRunResults[A]]
    protected[this] def run()(implicit runContext: RunContext): Future[RunResults[A]]

    lazy val dryRunResults: Future[DryRunResults[A]] = dryRun()
    lazy val runResults: Future[RunResults[A]] = run()
  }

  def mapInput[A, B](fn: A => B) = new OpenMandate[A, B] {
    override def bind(in: MandateInput[A])(implicit runContext: RunContext) =
      new SimpleLogicMandate[A, B](in) {
        override protected[this] def dryRunLogic(in: A)(implicit runContext: RunContext) = Some(fn(in))
        override protected[this] def runLogic(in: A)(implicit runContext: RunContext) = fn(in)
      }
  }

  abstract class SimpleLogicMandate[MI, MO](upstream: MandateInput[MI])(implicit runContext: RunContext) extends Mandate[MO] {
    protected[this] def dryRunLogic(in: MI)(implicit runContext: RunContext): Option[MO]
    protected[this] def runLogic(in: MI)(implicit runContext: RunContext): MO

    override protected[this] def dryRun()(implicit runContext: RunContext) =
      upstream.dryRunResults map { drr =>
        drr flatMap { i =>
          dryRunLogic(i).map(Unneeded.apply).getOrElse(Needed)
        }
      }

    override protected[this] def run()(implicit runContext: RunContext) =
      upstream.runResults map { rr =>
        rr flatMap { i =>
          Done(runLogic(i))
        }
      }
  }

  class GenericOpenMandate[I, O](name: String,
                             dryRunDelay: FiniteDuration, dryRunLogicFn: I => Option[O],
                             runDelay: FiniteDuration, runLogicFn: I => O)
    extends OpenMandate[I, O]
  {
    class GenericMandate(upstream: MandateInput[I])(implicit val runContext: RunContext)
      extends SimpleLogicMandate[I, O](upstream)(runContext)
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

    override def bind(in: MandateInput[I])(implicit runContext: RunContext) = new GenericMandate(in)
  }

  object MandateLibrary {

    object WriteRemoteFile {
      case class Input(path: String, content: String)

      def bind(in: MandateInput[Input])(implicit rc: RunContext): Mandate[Unit] = (new WriteRemoteFile).bind(in)
    }

    class WriteRemoteFile extends OpenMandate[WriteRemoteFile.Input, Unit] {

      override def bind(in: MandateInput[WriteRemoteFile.Input])(implicit runContext: RunContext) =
        new SimpleLogicMandate[WriteRemoteFile.Input, Unit](in) {
          override protected[this] def dryRunLogic(in: WriteRemoteFile.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug("WRF: see if file is already present")
            None
          }

          override protected[this] def runLogic(in: WriteRemoteFile.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug("WRF: sending the file")
            Thread.sleep(3000)
            log.debug("WRF: file sent")
          }
        }
    }

    object InstallAptKey {
      case class Input(keyserver: String, fingerprint: String)

      def bind(in: MandateInput[Input])(implicit rc: RunContext) = (new InstallAptKey).bind(in)
    }

    class InstallAptKey extends OpenMandate[InstallAptKey.Input, Unit] {
      override def bind(in: MandateInput[InstallAptKey.Input])(implicit runContext: RunContext): Mandate[Unit] =
        new SimpleLogicMandate[InstallAptKey.Input, Unit](in) {
          override protected[this] def dryRunLogic(in: InstallAptKey.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"IAK: see if apt key ${in.fingerprint} is already installed")
            None
          }

          override protected[this] def runLogic(in: InstallAptKey.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"IAK: install the apt key ${in.fingerprint}")
            Thread.sleep(3000)
            log.debug("IAK: installed key")
          }
        }
    }

    object UpdateAptGet {
      case class Input(refreshInterval: Duration)

      def bind(in: MandateInput[Input])(implicit rc: RunContext) = (new UpdateAptGet).bind(in)
    }

    class UpdateAptGet extends OpenMandate[UpdateAptGet.Input, Unit] {

      override def bind(in: MandateInput[UpdateAptGet.Input])(implicit runContext: RunContext) =
        new SimpleLogicMandate[UpdateAptGet.Input, Unit](in) {
          override protected[this] def dryRunLogic(in: UpdateAptGet.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"UAG: see if apt-get update has been run within the last ${in.refreshInterval}")
            None
          }

          override protected[this] def runLogic(in: UpdateAptGet.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"UAG: run apt-get update")
            Thread.sleep(3000)
            log.debug("UAG: ran apt-get update")
          }
        }
    }

    object InstallPackage {
      case class Input(name: String, version: Option[String] = None)
      case class Output(installedVersion: String)

      def bind(in: MandateInput[Input])(implicit rc: RunContext) = (new InstallPackage).bind(in)
    }

    class InstallPackage extends OpenMandate[InstallPackage.Input, InstallPackage.Output] {

      override def bind(in: MandateInput[InstallPackage.Input])(implicit runContext: RunContext) =
        new SimpleLogicMandate[InstallPackage.Input, InstallPackage.Output](in) {
          override protected[this] def dryRunLogic(in: InstallPackage.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"IP: see if package $in is installed")
            None
          }

          override protected[this] def runLogic(in: InstallPackage.Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"IP: install package $in")
            Thread.sleep(3000)
            log.debug(s"IP: installed package $in")
            InstallPackage.Output(in.version.getOrElse("1.0.0"))
          }
        }
    }

    object InstallJava8 {
      case class Input(version: Option[String] = None)
      case class Output(version: String)

      def bind(in: MandateInput[Input] = Input())(implicit rc: RunContext) = (new InstallJava8).bind(in)
    }

    class InstallJava8 extends OpenMandate[InstallJava8.Input, InstallJava8.Output] {

      override def bind(in: MandateInput[InstallJava8.Input])(implicit runContext: RunContext) = {
        val wrf =
          WriteRemoteFile.bind(
            WriteRemoteFile.Input(
              "/etc/apt/sources.list.d/webupd8team-java-trusty.list",
              "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
            )
          )

        val iak =
          InstallAptKey.bind(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B0889BF5709A105D03AC2518248EEA14886"))

        ( wrf join iak ) flatMap
          UpdateAptGet.bind(UpdateAptGet.Input(5 seconds)) flatMap
          InstallPackage.bind(InstallPackage.Input("oracle-java8-installer")) map { v =>
            InstallJava8.Output(v.installedVersion)
          }
      }
    }

    object InstallSbt {
      case class Input(version: Option[String] = None)
      case class Output(version: String)

      def bind(in: MandateInput[Input] = Input())(implicit rc: RunContext) = (new InstallSbt).bind(in)
    }

    class InstallSbt extends OpenMandate[InstallSbt.Input, InstallSbt.Output] {

      override def bind(in: MandateInput[InstallSbt.Input])(implicit runContext: RunContext) = {
        val wrf =
          WriteRemoteFile.bind(
            WriteRemoteFile.Input(
              "/etc/apt/sources.list.d/webupd8team-java-trusty.list",
              "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
            )
          )

        val iak =
          InstallAptKey.bind(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B5889BF5709A105D03AC2518248EEA14886"))

        ( wrf join iak ) flatMap
          UpdateAptGet.bind(UpdateAptGet.Input(5 seconds)) flatMap
          InstallPackage.bind(InstallPackage.Input("sbt", Some("0.13.1"))) map { v =>
            InstallSbt.Output(v.installedVersion)
          }
      }
    }

    object InstallSoftware {
      case class Input(sbtVersion: Option[String] = None, javaVersion: Option[String] = None)

      def bind(in: Input = Input())(implicit runContext: RunContext) = (new InstallSoftware).bind(in)
    }

    class InstallSoftware extends OpenMandate[InstallSoftware.Input, Unit] {

      override def bind(in: MandateInput[InstallSoftware.Input])(implicit runContext: RunContext) = {
        val m1 = InstallJava8.bind()
        val m2 = InstallSbt.bind()
        ( m1 join m2 ) map ( _ => Unit )
      }
    }
  }

  def main(args: Array[String]): Unit = {
    Logging.initialize()

    implicit val rc = new RunContext

    val seed = MandateInput.fromLiteral(())

    val af = new GenericOpenMandate[Unit, Int]("a", 0 seconds, { _ => None }, 3 seconds, { _ => 8})
    val a = af.bind(())
    val bf = new GenericOpenMandate[Int, String]("b", 0 seconds, { n => Some( ( "b" * n ) ) }, 3 seconds, { n => "b" * n })
    val m = bf.bind(a)

    println(Await.result(m.dryRunResults, Duration.Inf))

    println(Await.result(m.runResults, Duration.Inf))

    val xf = new GenericOpenMandate[Unit, Int]("x", 100 millis, { _ =>  Some(6) }, 1 second, { _ => 6 })
    val x = xf.bind(seed)

    val y = ( a join xf.bind(seed) )

    println(Await.result(y.dryRunResults, Duration.Inf))

    println(Await.result(y.runResults, Duration.Inf))

    val j = MandateLibrary.InstallSoftware.bind()
    println(Await.result(j.dryRunResults, Duration.Inf))
    println(Await.result(j.runResults, Duration.Inf))

    println(Await.result(j.runResults, Duration.Inf))
  }
}
