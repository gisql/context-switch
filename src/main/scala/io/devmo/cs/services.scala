package io.devmo.cs

import java.security.SecureRandom

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Random

trait Service {
  def fast(ctx: FlowContext): Future[Boolean]
  def medium(ctx: FlowContext): Future[Boolean]
  def slow(ctx: FlowContext): Future[Boolean]
  def randomFail(ctx: FlowContext): Future[Boolean]
  def mix(ctx: FlowContext): Future[Boolean]
  def load(ctx: FlowContext): Future[Boolean]
}

class NonBlocking(implicit val ec: ExecutionContext) extends Service {
  def fast(ctx: FlowContext) = Future {
    FlowContextHolder.verify(ctx)
  }

  private def delay(max: Int): Unit = {
    var i: Int = max
    val rnd = new SecureRandom()
    while (i > 0) {
      i = i - 1
      rnd.nextLong()
    }
  }

  def medium(ctx: FlowContext) = Future {
    delay(1000)
    FlowContextHolder.verify(ctx)
  }

  def slow(ctx: FlowContext) = Future {
    delay(100000)
    FlowContextHolder.verify(ctx)
  }

  def randomFail(ctx: FlowContext): Future[Boolean] = {
    def loop = Future {
      delay(100)
      if (Random.nextBoolean()) throw new IllegalArgumentException
      else FlowContextHolder.verify(ctx)
    }

    loop recoverWith {
      case _ => randomFail(ctx)
    }
  }

  def mix(ctx: FlowContext): Future[Boolean] = Utils.sequencer(ctx, slow, medium, fast)
  def load(ctx: FlowContext): Future[Boolean] = Utils.multiply(ctx, 2000, fast, medium, randomFail)
}

class Blocking(implicit val ec: ExecutionContext) extends Service {
  def fast(ctx: FlowContext) = Future {
    blocking {
      Thread.sleep(1)
      FlowContextHolder.verify(ctx)
    }
  }

  def medium(ctx: FlowContext) = Future {
    blocking {
      Thread.sleep(10)
      FlowContextHolder.verify(ctx)
    }
  }

  def slow(ctx: FlowContext) = Future {
    blocking {
      Thread.sleep(200)
      FlowContextHolder.verify(ctx)
    }
  }

  def randomFail(ctx: FlowContext): Future[Boolean] = {
    def loop = Future {
      Thread.sleep(1)
      if (Random.nextBoolean()) throw new IllegalArgumentException
      else FlowContextHolder.verify(ctx)
    }

    loop recoverWith {
      case _ => randomFail(ctx)
    }
  }

  def mix(ctx: FlowContext): Future[Boolean] = Utils.sequencer(ctx, slow, medium, fast)
  def load(ctx: FlowContext): Future[Boolean] = Utils.multiply(ctx, 2000, fast, medium, randomFail)
}

class Mixer(services: Service*)(implicit val ec: ExecutionContext) extends Service {
  override def fast(ctx: FlowContext): Future[Boolean] = Utils.parallel(ctx, services.map(s => s.fast _): _*)
  override def medium(ctx: FlowContext): Future[Boolean] = Utils.parallel(ctx, services.map(s => s.medium _): _*)
  override def slow(ctx: FlowContext): Future[Boolean] = Utils.parallel(ctx, services.map(s => s.slow _): _*)
  override def randomFail(ctx: FlowContext): Future[Boolean] = Utils.parallel(ctx, services.map(s => s.randomFail _): _*)
  override def mix(ctx: FlowContext): Future[Boolean] = Utils.parallel(ctx, services.map(s => s.mix _): _*)
  override def load(ctx: FlowContext): Future[Boolean] = Utils.parallel(ctx, services.map(s => s.load _): _*)
}

object Utils {
  type FtGen = FlowContext => Future[Boolean]

  def sequencer(expected: FlowContext, xs: FtGen*)(implicit ec: ExecutionContext): Future[Boolean] =
    xs.foldLeft(Future.successful(true)) {
      case (acc, task) =>
        for {
          a <- acc
          b <- task(expected)
        } yield a && b && FlowContextHolder.verify(expected)
    }

  def parallel(expected: FlowContext, xs: FtGen*)(implicit ec: ExecutionContext): Future[Boolean] =
    Future.sequence(xs.map(x => x(expected)).toList).map(x => x.forall(x => x) && FlowContextHolder.verify(expected))

  def multiply(expected: FlowContext, times: Int, xs: FtGen*)(implicit ec: ExecutionContext): Future[Boolean] =
    parallel(expected, (1 to times).map(_ => (ex => parallel(ex, xs: _*)): FtGen): _*)
}
