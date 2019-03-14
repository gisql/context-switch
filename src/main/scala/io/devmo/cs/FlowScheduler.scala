package io.devmo.cs

import akka.actor.{Cancellable, Scheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class FlowScheduler(target: Scheduler) extends Scheduler {
  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)
                       (implicit executor: ExecutionContext): Cancellable =
    target.schedule(initialDelay, interval, RunnableWithFlow(runnable))
  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)
                           (implicit executor: ExecutionContext): Cancellable =
    target.scheduleOnce(delay, RunnableWithFlow(runnable))
  override def maxFrequency: Double = target.maxFrequency
}

object FlowScheduler {
  def apply(target: Scheduler): FlowScheduler = target match {
    case flowed: FlowScheduler => flowed
    case _ => new FlowScheduler(target)
  }
}
