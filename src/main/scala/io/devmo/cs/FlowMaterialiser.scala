package io.devmo.cs

import akka.actor.Cancellable
import akka.stream.{Attributes, ClosedShape, Graph, Materializer}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

class FlowMaterialiser(target: Materializer) extends Materializer {
  override def withNamePrefix(name: String): Materializer = target.withNamePrefix(name)
  override def materialize[Mat](graph: Graph[ClosedShape, Mat]): Mat = target.materialize(graph)
  override def materialize[Mat](graph: Graph[ClosedShape, Mat], attr: Attributes): Mat = target.materialize(graph, attr)

  override implicit def executionContext: ExecutionContextExecutor = FlowExecutionContext(target.executionContext)
  override def scheduleOnce(delay: FiniteDuration, task: Runnable): Cancellable = target.scheduleOnce(delay, RunnableWithFlow(task))
  override def schedulePeriodically(initialDelay: FiniteDuration, interval: FiniteDuration, task: Runnable): Cancellable =
    target.schedulePeriodically(initialDelay, interval, RunnableWithFlow(task))
}

object FlowMaterialiser {
  def apply(target: Materializer): FlowMaterialiser = target match {
    case flowed: FlowMaterialiser => flowed
    case _ => new FlowMaterialiser(target)
  }
}
