package io.devmo.cs

import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

case class FlowContext(flowId: String)

object FlowContextHolder {
  private val holder = new ThreadLocal[FlowContext]() {
    override def initialValue(): FlowContext = FlowContext("unknown")
  }
  private val al = new AtomicLong()

  def current(): FlowContext = holder.get()
  def swap(x: FlowContext): FlowContext = {
    val rv = current()
    holder.set(x)
    rv
  }

  def verify(expected: FlowContext): Boolean = {
    val ok = expected == current()
    if (!ok) al.incrementAndGet()
    ok
  }

  def reset(): Unit = al.set(0)
  def failCount: Long = al.get()
}

class FlowExecutionContext(target: ExecutionContext) extends ExecutionContextExecutor {
  override def execute(runnable: Runnable): Unit = {
    val callerFlow = FlowContextHolder.current()

    target.execute(() => {
      val previousFlow = FlowContextHolder.swap(callerFlow)
      try {
        runnable.run()
      } finally {
        FlowContextHolder.swap(previousFlow)
      }
    })
  }

  override def reportFailure(cause: Throwable): Unit = target.reportFailure(cause)
}

object FlowExecutionContext {
  def apply(target: ExecutionContext): FlowExecutionContext = target match {
    case x: FlowExecutionContext => x
    case _ => new FlowExecutionContext(target)
  }
}
