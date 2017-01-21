package org.scalawag.jibe.outputs

import scala.concurrent.ExecutionContext

trait OpenMandate[IN, OUT] { me =>
  def bind(in: MandateInput[IN])(implicit runContext: RunContext): Mandate[OUT]

  def flatMap[YOUT](you: OpenMandate[OUT, YOUT]) =
    new OpenMandate[IN, YOUT] {
      override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
        you.bind(me.bind(in))
    }

  def join[YOUT](you: OpenMandate[IN, YOUT])(implicit executionContext: ExecutionContext) =
    new OpenMandate[IN, (OUT, YOUT)] {
      override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
        me.bind(in) join you.bind(in)
    }
}
