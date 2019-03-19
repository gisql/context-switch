package io.devmo.cs

class RunnableWithFlow(target: Runnable) extends Runnable {
  private val callerFlow = FlowContextHolder.current

  override def run(): Unit = {
    val previous = FlowContextHolder.swap(callerFlow)
    target.run()
    FlowContextHolder.swap(previous)
  }
}

object RunnableWithFlow {
  def apply(target: Runnable): RunnableWithFlow = target match {
    case flowed: RunnableWithFlow => flowed
    case _ => new RunnableWithFlow(target)
  }
}
