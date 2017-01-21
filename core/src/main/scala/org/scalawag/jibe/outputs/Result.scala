package org.scalawag.jibe.outputs

object DryRun {
  trait Result[+A] {
    def map[B](fn: A => B): Result[B]
    def flatMap[B](fn: A => Result[B]): Result[B]
  }

  case class Unneeded[A](output: A) extends Result[A] {
    override def map[B](fn: A => B): Result[B] = Unneeded(fn(output))
    override def flatMap[B](fn: A => Result[B]): Result[B] = fn(output)
  }

  case object Needed extends Result[Nothing] {
    override def map[B](fn: (Nothing) => B) = Needed
    override def flatMap[B](fn: (Nothing) => Result[B]) = Needed
  }

  case object Blocked extends Result[Nothing] {
    override def map[B](fn: Nothing => B): Result[B] = Blocked
    override def flatMap[B](fn: (Nothing) => Result[B]) = Blocked
  }
}

object Run {
  trait Result[+A] {
    def map[B](fn: A => B): Result[B]
    def flatMap[B](fn: A => Result[B]): Result[B]
  }

  case class Unneeded[A](output: A) extends Result[A] {
    override def map[B](fn: A => B): Result[B] = Unneeded(fn(output))
    override def flatMap[B](fn: A => Result[B]): Result[B] = fn(output)
  }
  
  case class Done[A](output: A) extends Result[A] {
    override def map[B](fn: A => B): Result[B] = Done(fn(output))
    override def flatMap[B](fn: A => Result[B]): Result[B] = fn(output)
  }
  
  case object Blocked extends Result[Nothing] {
    override def map[B](fn: Nothing => B): Result[B] = Blocked
    override def flatMap[B](fn: (Nothing) => Result[B]) = Blocked
  }
}
