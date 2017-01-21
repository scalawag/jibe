package org.scalawag.jibe.outputs


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}



object OutputsTest {
  // TODO: handle reporting/structure/metadata
  // TODO: make the implementation interface prettier
  // TODO: maybe hide HLists as argument lists/tuples


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

    override protected[this] def dryRun()(implicit runContext: RunContext) = {
      import DryRun._
      upstream.dryRunResults map { drr =>
        drr flatMap { i =>
          dryRunLogic(i).map(Unneeded.apply).getOrElse(Needed)
        }
      }
    }

    override protected[this] def run()(implicit runContext: RunContext) = {
      import Run._
      upstream.runResults map { rr =>
        rr flatMap { i =>
          Done(runLogic(i))
        }
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
