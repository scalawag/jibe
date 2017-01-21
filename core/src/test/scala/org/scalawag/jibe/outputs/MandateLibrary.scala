package org.scalawag.jibe.outputs

import scala.concurrent.duration._

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
