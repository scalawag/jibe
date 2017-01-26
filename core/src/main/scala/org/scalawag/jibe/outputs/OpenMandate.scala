package org.scalawag.jibe.outputs

trait OpenMandate[-IN, +OUT] { me =>
  /** Binds this open mandate to a given input, producing a mandate which is ready to be executed. */
  def bind(in: MandateInput[IN])(implicit runContext: RunContext): MandateInput[OUT]

  def map[B](fn: OUT => B)(implicit runContext: RunContext): OpenMandate[IN, B] =
    new OpenMandate[IN, B] {
      override def bind(in: MandateInput[IN])(implicit runContext: RunContext) =
        me.bind(in) map fn
    }

  /** Creates a new OpenMandate that binds the output of this one to the specified one. */
  def flatMap[YOUT](you: OpenMandate[OUT, YOUT]): OpenMandate[IN, YOUT] =
    new OpenMandate[IN, YOUT] {
      override def bind(in: MandateInput[IN])(implicit runContext: RunContext): MandateInput[YOUT] =
        you.bind(me.bind(in))
    }

  /** Creates a new OpenMandate that, when bound, will produce two joined mandates (executed in parallel). */
  def join[YIN <: IN, YOUT](you: OpenMandate[YIN, YOUT]): OpenMandate[YIN, (OUT, YOUT)] =
    new OpenMandate[YIN, (OUT, YOUT)] {
      override def bind(in: MandateInput[YIN])(implicit runContext: RunContext): MandateInput[(OUT, YOUT)] =
        me.bind(in) join you.bind(in)
    }
}
