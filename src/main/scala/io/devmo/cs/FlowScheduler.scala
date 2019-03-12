package io.devmo.cs

import akka.actor.{Cancellable, Scheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class FlowScheduler(target: Scheduler) extends Scheduler {
  class AddCurrentFlow(r: Runnable) extends Runnable {
    private val callerFlow = FlowContextHolder.current()

    override def run(): Unit = {
      val previous = FlowContextHolder.swap(callerFlow)
      r.run()
      FlowContextHolder.swap(previous)
    }
  }

  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)
                       (implicit executor: ExecutionContext): Cancellable =
    target.schedule(initialDelay, interval, new AddCurrentFlow(runnable))
  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)
                           (implicit executor: ExecutionContext): Cancellable =
    target.scheduleOnce(delay, new AddCurrentFlow(runnable))
  override def maxFrequency: Double = target.maxFrequency
}

object FlowScheduler {
  def apply(target: Scheduler): FlowScheduler = target match {
    case flowed: FlowScheduler => flowed
    case _ => new FlowScheduler(target)
  }
}
