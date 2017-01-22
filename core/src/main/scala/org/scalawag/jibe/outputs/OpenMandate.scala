package org.scalawag.jibe.outputs

trait OpenMandate[IN, OUT] { me =>
  /** Binds this open mandate to a given input, producing a mandate which is ready to be executed. */
  def bind(in: MandateInput[IN])(implicit runContext: RunContext): MandateInput[OUT]

  /** Creates a new OpenMandate that binds the output of this one to the specified one. */
  def flatMap[YOUT](you: OpenMandate[OUT, YOUT]) =
    new OpenMandate[IN, YOUT] {
      override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
        you.bind(me.bind(in))
    }

  /** Creates a new OpenMandate that, when bound, will produce two joined mandates (executed in parallel). */
  def join[YOUT](you: OpenMandate[IN, YOUT]) =
    new OpenMandate[IN, (OUT, YOUT)] {
      override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
        me.bind(in) join you.bind(in)
    }
}
