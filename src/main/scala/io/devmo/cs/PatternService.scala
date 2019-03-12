package io.devmo.cs

import akka.actor.{ActorSystem, Scheduler}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class PatternService(as: ActorSystem)(implicit ec: ExecutionContext) extends Service {
  private implicit val scheduler: Scheduler = FlowScheduler(as.scheduler)

  private def delay(ctx: FlowContext, ms: Int): Future[Boolean] = {
    akka.pattern.after(ms.millisecond, scheduler) {
      Future {
        FlowContextHolder.verify(ctx)
      }
    }
  }
  override def fast(ctx: FlowContext): Future[Boolean] = delay(ctx, 1)
  override def medium(ctx: FlowContext): Future[Boolean] = delay(ctx, 10)
  override def slow(ctx: FlowContext): Future[Boolean] = delay(ctx, 200)

  override def randomFail(ctx: FlowContext): Future[Boolean] = {
    def loop: Future[Boolean] = akka.pattern.after(1.millisecond, scheduler) {
      Future {
        if (Random.nextBoolean()) throw new IllegalArgumentException
        else FlowContextHolder.verify(ctx)
      }
    }

    akka.pattern.retry(() => loop, 10000, 1.millisecond)
  }
  def mix(ctx: FlowContext): Future[Boolean] = Utils.sequencer(ctx, slow, medium, fast, randomFail)
  def load(ctx: FlowContext): Future[Boolean] = Utils.multiply(ctx, 2000, fast, medium, randomFail)
}
