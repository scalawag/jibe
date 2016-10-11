package org.scalawag.jibe.backend.bash

import org.scalawag.jibe.mandate.command.CommandArgument._

object BashCommander {
  def bashify(structure: StructureValue): Iterable[String] = {
    def atomic(v: AtomicValue) =
      v match {
        case StringValue(s) => s""""$s""""
        case LongValue(l) => l.toString
        case BooleanValue(true) => "t"
        case BooleanValue(false) => ""
      }

    def helper(values: Iterable[(String, String, Value)], acc: Iterable[String] = Iterable.empty): Iterable[String] =
      if (values.isEmpty) {
        acc
      } else {
        val head = values.head
        val tail = values.tail
        head match {
          case (prefix, k, a: AtomicValue) =>
            helper(tail, acc ++ Iterable(s"${prefix}$k=${atomic(a)}"))
          case (prefix, k, TraversableValue(l)) =>
            helper(tail, acc ++ Iterable(l.map(atomic).mkString(s"${prefix}$k=(", " ", ")")))
          case (prefix, k, StructureValue(vs)) =>
            helper(vs.map(v => (s"${k}_$prefix", v._1, v._2)) ++ tail, acc)
        }
      }

    helper(structure.values.map(v => ("", v._1, v._2)))
  }
}
