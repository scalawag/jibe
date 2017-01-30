package org.scalawag.jibe.outputs

import java.io.PrintWriter

import scala.concurrent.duration._

object MandateLibrary {

  object WriteRemoteFile {
    case class Input(path: String, content: String)

    object Mandate extends SimpleLogicMandate[Input, Unit] {

      override protected[this] def dryRunLogic(in: Input)(implicit runContext: RunContext): Option[Unit] =  {
        import runContext._
        log.debug("WRF: see if file is already present")
        None
      }

      override protected[this] def runLogic(in: Input)(implicit runContext: RunContext): Unit = {
        import runContext._
        log.debug("WRF: sending the file")
        Thread.sleep(3000)
        log.debug("WRF: file sent")
      }

      override val toString: String = s"WriteRemoteFile"
    }
  }

  object InstallAptKey {
    case class Input(keyserver: String, fingerprint: String)

    object Mandate extends SimpleLogicMandate[InstallAptKey.Input, Unit] {
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

    object Mandate extends SimpleLogicMandate[Input, Unit] {
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

  object InstallPackage {
    case class Input(name: String, version: Option[String] = None)
    case class Output(installedVersion: String)

    object Mandate extends SimpleLogicMandate[Input, Output] {
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

  object InstallJava8 {
    case class Input(version: Option[String] = None)
    case class Output(version: String)

    val PreAptGetUpdate = {
      val in = Mandate[Input]

      val wrf =
        in map { _ =>
          WriteRemoteFile.Input(
            "/etc/apt/sources.list.d/webupd8team-java-trusty.list",
            "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
          )
        } flatMap WriteRemoteFile.Mandate

      val iak =
        in map { _ =>
          InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B5889BF5709A105D03AC2518248EEA14886")
        } flatMap InstallAptKey.Mandate

      ( wrf join iak ) map ( _ => () )
    }

    val PostAptGetUpdate =
      Mandate[Input] map { _ =>
        InstallPackage.Input("oracle-java8-installer")
      } flatMap {
        InstallPackage.Mandate
      } map { v =>
        Output(v.installedVersion)
      }

    val Complete = {
      val in = Mandate[Input]

      in map { _ =>
        UpdateAptGet.Input()
      } flatMap {
        UpdateAptGet.Mandate
      } replace {
        in
      } flatMap {
        PostAptGetUpdate
      }
    }
  }

  object InstallSbt {
    case class Input(version: Option[String] = None)
    case class Output(version: String)

    val PreAptGetUpdate =
      Mandate[Input] map { _ =>
        WriteRemoteFile.Input(
          "/etc/apt/sources.list.d/webupd8team-java-trusty.list",
          "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
        )
      } flatMap {
        WriteRemoteFile.Mandate
      } map { _ =>
        InstallAptKey.Input("keyserver.ubuntu.com", "7B2C3B5889BF5709A105D03AC2518248EEA14886")
      } flatMap {
        InstallAptKey.Mandate
      }

    val PostAptGetUpdate =
      Mandate[Input] map { _ =>
        InstallPackage.Input("sbt", Some("0.13.1"))
      } flatMap {
        InstallPackage.Mandate
      } map { v =>
        InstallSbt.Output(v.installedVersion)
      }

    val Complete = {
      val in = Mandate[Input]

      in flatMap {
        PreAptGetUpdate
      } map { _ =>
        UpdateAptGet.Input()
      } flatMap {
        UpdateAptGet.Mandate
      } replace {
        in
      } flatMap {
        PostAptGetUpdate
      }
    }
  }

  object InstallSoftware {
    case class Input(sbtVersion: Option[String] = None, javaVersion: Option[String] = None)

    val Full = {
      val in = Mandate[Input]

      val java = in map { _ => InstallJava8.Input() } flatMap InstallJava8.Complete
      val sbt = in map { _ => InstallSbt.Input() } flatMap InstallSbt.Complete
      ( java join sbt ) compositeAs("Install Software") map ( _ => () )
    }

    val Shared = {
      val in = Mandate[Input]

      val pres =
        (
          ( in map { i => InstallJava8.Input(i.javaVersion) } flatMap InstallJava8.PreAptGetUpdate )
            join
          ( in map { i => InstallSbt.Input(i.sbtVersion) } flatMap InstallSbt.PreAptGetUpdate )
        )

      val mid = pres.map( _ => UpdateAptGet.Input() ) flatMap UpdateAptGet.Mandate replace in

      val post =
        (
          ( mid map { i => InstallJava8.Input(i.javaVersion) } flatMap InstallJava8.PostAptGetUpdate )
            join
          ( mid map { i => InstallSbt.Input(i.sbtVersion) } flatMap InstallSbt.PostAptGetUpdate )
        ) compositeAs("Install Software") map { _ => () }

      post
    }
  }
}
