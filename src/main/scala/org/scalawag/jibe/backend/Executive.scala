package org.scalawag.jibe.backend

object Executive {
  def apply(rootMandate: Mandate, ssh: SSHConnectionInfo, commander: Commander) = {

    def execute(mandate: Mandate): MandateResults =
      mandate match {
        case CompositeMandate(desc, innards@_*) =>
          val results = innards.map(execute)
          val outcome =
            if ( results.exists(_.outcome == MandateResults.Outcome.FAILURE) )
              MandateResults.Outcome.FAILURE
            else if ( results.forall(_.outcome == MandateResults.Outcome.USELESS) )
              MandateResults.Outcome.USELESS
            else
              MandateResults.Outcome.SUCCESS
          MandateResults(mandate, outcome, Left(results))
        case m =>
          val command = commander.getCommand(m)
          val results = command.perform(ssh)
          val outcome =
            if ( results.exitCode == 0 )
              MandateResults.Outcome.SUCCESS
            else
              MandateResults.Outcome.FAILURE
          MandateResults(mandate, outcome, Right(results))
      }

    execute(rootMandate)
  }
}
