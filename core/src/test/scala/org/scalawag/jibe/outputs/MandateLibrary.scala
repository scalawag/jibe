package org.scalawag.jibe.outputs

import java.io.PrintWriter

import scala.concurrent.duration._

object MandateLibrary {

  object WriteRemoteFile {
    case class Input(path: String, content: String)

    def bind(in: MandateInput[Input])(implicit rc: RunContext): Mandate[Unit] = (new WriteRemoteFile).bind(in)
  }

  class WriteRemoteFile extends OpenMandate[WriteRemoteFile.Input, Unit] {

    override def bind(in: MandateInput[WriteRemoteFile.Input])(implicit runContext: RunContext) =
      new SimpleLogicMandate[WriteRemoteFile.Input, Unit](in) {

        override val inputs: Set[MandateInput[_]] = Set(in)
        override val toString: String = s"WriteRemoteFile($in)"

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

        override val inputs: Set[MandateInput[_]] = Set(in)
        override val toString: String = s"InstallAptKey($in)"

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
    case class Input(refreshInterval: Duration = 0 seconds)

    object OpenMandate extends org.scalawag.jibe.outputs.OpenMandate[Input, Unit] {

      override def bind(in: MandateInput[Input])(implicit runContext: RunContext) =

        new SimpleLogicMandate[Input, Unit](in) {

          override val inputs: Set[MandateInput[_]] = Set(in)
          override val toString: String = s"UpdateAptGet($in)"

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
  }

  object InstallPackage {
    case class Input(name: String, version: Option[String] = None)
    case class Output(installedVersion: String)

    object OpenMandate extends org.scalawag.jibe.outputs.OpenMandate[Input, Output] {

      override def bind(in: MandateInput[Input])(implicit runContext: RunContext) =
        new SimpleLogicMandate[Input, Output](in) {

          override val inputs: Set[MandateInput[_]] = Set(in)
          override val toString: String = s"InstallPackage($in)"

          override protected[this] def dryRunLogic(in: Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"IP: see if package $in is installed")
            None
          }

          override protected[this] def runLogic(in: Input)(implicit runContext: RunContext) = {
            import runContext._
            log.debug(s"IP: install package $in")
            Thread.sleep(3000)
            log.debug(s"IP: installed package $in")
            Output(in.version.getOrElse("1.0.0"))
          }
        }

    }
  }

  object InstallJava8 {
    case class Input(version: Option[String] = None)
    case class Output(version: String)


    object Complete extends org.scalawag.jibe.outputs.OpenMandate[Input, Output] {
      override def bind(in: MandateInput[Input])(implicit runContext: RunContext) =
        PreAptGetUpdate.bind(in).
          map(_ => UpdateAptGet.Input()).
          flatMap(UpdateAptGet.OpenMandate).
          flatMap(_ => in).
          flatMap(PostAptGetUpdate)
    }

    object PreAptGetUpdate extends org.scalawag.jibe.outputs.OpenMandate[Input, Unit] {
      override def bind(in: MandateInput[Input])(implicit runContext: RunContext): MandateInput[Unit] = {
        val wrf =
          WriteRemoteFile.bind(
            WriteRemoteFile.Input(
              "/etc/apt/sources.list.d/webupd8team-java-trusty.list",
              "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
            )
          )

        val iak = InstallAptKey.bind(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B5889BF5709A105D03AC2518248EEA14886"))

        ( wrf join iak ) map ( _ => () )
      }
    }

    object PostAptGetUpdate extends org.scalawag.jibe.outputs.OpenMandate[Input, Output] {
      /** Binds this open mandate to a given input, producing a mandate which is ready to be executed. */
      override def bind(in: MandateInput[Input])(implicit runContext: RunContext): MandateInput[Output] = {
        in.map(_ => InstallPackage.Input("oracle-java8-installer")).flatMap(InstallPackage.OpenMandate).map { v =>
          Output(v.installedVersion)
        } compositeAs("Install Java")
      }
    }
  }

  object InstallSbt {
    case class Input(version: Option[String] = None)
    case class Output(version: String)

    object Full extends OpenMandate[Input, Output] {
      override def bind(in: MandateInput[Input])(implicit runContext: RunContext) =
        PreAptGetUpdate.bind(in).
          map(_ => UpdateAptGet.Input()).
          flatMap(UpdateAptGet.OpenMandate).
          flatMap(_ => in).
          flatMap(PostAptGetUpdate)
    }

    object PreAptGetUpdate extends OpenMandate[Input, Unit] {
      override def bind(in: MandateInput[Input])(implicit runContext: RunContext): MandateInput[Unit] = {
        val wrf =
          WriteRemoteFile.bind(
            WriteRemoteFile.Input(
              "/etc/apt/sources.list.d/webupd8team-java-trusty.list",
              "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
            )
          )

        val iak = InstallAptKey.bind(InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B5889BF5709A105D03AC2518248EEA14886"))

        ( wrf join iak ) map ( _ => () )
      }
    }

    object PostAptGetUpdate extends OpenMandate[Input, Output] {
      /** Binds this open mandate to a given input, producing a mandate which is ready to be executed. */
      override def bind(in: MandateInput[Input])(implicit runContext: RunContext): MandateInput[Output] = {
        in.map(_ => InstallPackage.Input("sbt", Some("0.13.1"))).flatMap(InstallPackage.OpenMandate) map { v =>
          InstallSbt.Output(v.installedVersion)
        } compositeAs("Install sbt")
      }
    }
  }

  object InstallSoftware {
    case class Input(sbtVersion: Option[String] = None, javaVersion: Option[String] = None)

    def bind(in: Input = Input())(implicit runContext: RunContext) = (new InstallSoftware).bind(in)
  }

  class InstallSoftware extends OpenMandate[InstallSoftware.Input, Unit] {

    override def bind(in: MandateInput[InstallSoftware.Input])(implicit runContext: RunContext) = {
      val m1 = InstallJava8.Complete.bind(InstallJava8.Input())
      val m2 = InstallSbt.Full.bind(InstallSbt.Input())
      ( m1 join m2 ) compositeAs("Install Software") map ( _ => () )
    }
  }

  object InstallSoftwareSharedAptGetUpdate extends OpenMandate[InstallSoftware.Input, Unit] {
    override def bind(in: MandateInput[InstallSoftware.Input])(implicit runContext: RunContext) = {
      val pre =
        (
          ( in map { i => InstallJava8.Input(i.javaVersion) } flatMap InstallJava8.PreAptGetUpdate )
            join
          ( in map { i => InstallSbt.Input(i.sbtVersion) } flatMap InstallSbt.PreAptGetUpdate )
        )

      val mid = pre.map( _ => UpdateAptGet.Input() ).flatMap(UpdateAptGet.OpenMandate).flatMap( _ => in )

      val post =
        (
          ( mid flatMap { i => InstallJava8.Input(i.javaVersion) } flatMap InstallJava8.PostAptGetUpdate )
            join
          ( mid flatMap { i => InstallSbt.Input(i.sbtVersion) } flatMap InstallSbt.PostAptGetUpdate )
        ) compositeAs("Install Software") map { _ => () }

      post
    }
  }
}
