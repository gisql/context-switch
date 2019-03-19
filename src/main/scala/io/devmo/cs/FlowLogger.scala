package io.devmo.cs

trait FlowLogger {
  def trace(msg: => String)(implicit ctx: FlowContext)
  def trace(msg: => String, e: Throwable)(implicit ctx: FlowContext)
  def debug(msg: => String)(implicit ctx: FlowContext)
  def debug(msg: => String, e: Throwable)(implicit ctx: FlowContext)
  def info(msg: => String)(implicit ctx: FlowContext)
  def info(msg: => String, e: Throwable)(implicit ctx: FlowContext)
  def warn(msg: => String)(implicit ctx: FlowContext)
  def warn(msg: => String, e: Throwable)(implicit ctx: FlowContext)
  def error(msg: => String)(implicit ctx: FlowContext)
  def error(msg: => String, e: Throwable)(implicit ctx: FlowContext)
}

class PrintFlowLogger extends FlowLogger {
  override def trace(msg: => String)(implicit ctx: FlowContext): Unit = println("trace", msg, ctx)
  override def trace(msg: => String, e: Throwable)(implicit ctx: FlowContext): Unit = println("trace", msg, ctx)
  override def debug(msg: => String)(implicit ctx: FlowContext): Unit = println("debug", msg, ctx)
  override def debug(msg: => String, e: Throwable)(implicit ctx: FlowContext): Unit = println("debug", msg, ctx)
  override def info(msg: => String)(implicit ctx: FlowContext): Unit = println("info", msg, ctx)
  override def info(msg: => String, e: Throwable)(implicit ctx: FlowContext): Unit = println("info", msg, ctx)
  override def warn(msg: => String)(implicit ctx: FlowContext): Unit = println("warn", msg, ctx)
  override def warn(msg: => String, e: Throwable)(implicit ctx: FlowContext): Unit = println("warn", msg, ctx)
  override def error(msg: => String)(implicit ctx: FlowContext): Unit = println("error", msg, ctx)
  override def error(msg: => String, e: Throwable)(implicit ctx: FlowContext): Unit = println("error", msg, ctx)
}

// TODO
//  - add adapter for slf4j
//  - add akka adapter
//  - test non-eager msg

object Foo extends App {
  val xx = new PrintFlowLogger

  import FlowContextHolder.current

  xx.info("blurp")
  FlowContextHolder.swap(FlowContext("after"))
  xx.info("blurp")
}