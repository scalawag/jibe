package org.scalawag.jibe.mandate

import java.io.File

import org.scalawag.jibe.mandate.command.CommandArgument._
import scala.concurrent.duration._


package object command {
  implicit val StringToValue = new ToValue[String, StringValue] {
    override def toValue(s: String) = Some(new StringValue(s))
  }

  implicit val IntToValue = new ToValue[Int, LongValue] {
    override def toValue(n: Int) = Some(new LongValue(n))
  }

  implicit val LongToValue = new ToValue[Long, LongValue] {
    override def toValue(l: Long) = Some(new LongValue(l))
  }

  implicit val BooleanToValue = new ToValue[Boolean, BooleanValue] {
    override def toValue(b: Boolean) = Some(new BooleanValue(b))
  }

  implicit def OptionToValue[A, B <: Value](implicit itemToValue: ToValue[A, B]) = new ToValue[Option[A], B] {
    override def toValue(o: Option[A]) = o.flatMap(itemToValue.toValue)
  }

  implicit def TraversableToValue[A, B <: AtomicValue](implicit itemToValue: ToValue[A, B]) = new ToValue[Traversable[A], TraversableValue[B]] {
    override def toValue(i: Traversable[A]): Option[TraversableValue[B]] = Some(new TraversableValue[B](
      i.flatMap(itemToValue.toValue)
    ))
  }

  implicit val FileToValue = new ToValue[File, StringValue] {
    override def toValue(a: File): Option[StringValue] = Some(StringValue(a.getPath))
  }

  implicit val DurationToValue = new ToValue[Duration, LongValue] {
    override def toValue(d: Duration) = Some(new LongValue(d.toMillis))
  }

}
