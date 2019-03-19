package io.devmo.cs

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success, Try}

class StreamService(queueSize: Int, poolSize: Int)(implicit ec: ExecutionContext, mat: Materializer) extends Service {
  type ResponseTag = (Promise[Boolean], FlowContext)
  private val queue: SourceQueue[(Req, ResponseTag)] = {
    val outsideOfOurControl = OutsideOfOurControl.pooledFlow[ResponseTag](poolSize)

    Source.queue[(Req, ResponseTag)](queueSize, OverflowStrategy.backpressure)
      .via(outsideOfOurControl)
      .map({
        case (r, (p, flow)) =>
          FlowContextHolder.swap(flow)
          r -> p
      })
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(FlowContextHolder.verify(resp.requested))
        case (Failure(e), p) => p.failure(e)
      })(Keep.left).run()
  }

  private def execute(req: Req): Future[Boolean] = {
    val rv = (Promise[Boolean](), FlowContextHolder.current)
    queue.offer(req -> rv) flatMap {
      case QueueOfferResult.Enqueued =>
        val (p, ctx) = rv
        p.future.map(x => {
          FlowContextHolder.swap(ctx)
          x
        })
      case _ => Future.failed(new RuntimeException("Failed.  Increase queue size for tests"))
    }
  }

  override def fast(ctx: FlowContext): Future[Boolean] = execute(Req(ctx, 1))
  override def medium(ctx: FlowContext): Future[Boolean] = execute(Req(ctx, 10))
  override def slow(ctx: FlowContext): Future[Boolean] = execute(Req(ctx, 200))
  override def randomFail(ctx: FlowContext): Future[Boolean] = {
    def loop: Future[Boolean] = execute(Req(ctx, 1, Random.nextBoolean()))

    loop recoverWith { case _ => randomFail(ctx) }
  }
  def mix(ctx: FlowContext): Future[Boolean] = Utils.sequencer(ctx, slow, medium, fast)
  def load(ctx: FlowContext): Future[Boolean] = Utils.multiply(ctx, 2000, fast, medium, randomFail)
}

// This mimics some external library, with its 'protocol'.  The 'requested' fields to be used *only* for verification.
case class Req(requested: FlowContext, delayMs: Int, fail: Boolean = false)
case class Res(requested: FlowContext)
object OutsideOfOurControl {
  // functionally equivalent to Http().superPool[T](...)
  def pooledFlow[T](n: Int): Flow[(Req, T), (Try[Res], T), NotUsed] = poolOf(n)(syncFunction[T])

  private def poolOf[IN, OUT, ADD](n: Int)(workerFunc: ((IN, ADD)) => (Try[OUT], ADD)): Flow[(IN, ADD), (Try[OUT], ADD), NotUsed] = {
    val worker: Flow[(IN, ADD), (Try[OUT], ADD), NotUsed] = Flow.fromFunction(workerFunc)
    val graph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val fork = b.add(Balance[(IN, ADD)](n))
      val merge = b.add(Merge[(Try[OUT], ADD)](n))

      for (i <- 0 until n) fork.out(i) ~> worker.async ~> merge.in(i)

      FlowShape.of(fork.in, merge.out)
    }
    Flow.fromGraph(graph)
  }
  private def syncFunction[T](in: (Req, T)): (Try[Res], T) = in match {
    case (req, rv) =>
      Thread.sleep(req.delayMs)
      if (req.fail) Failure(new RuntimeException()) -> rv
      else Success(Res(req.requested)) -> rv
  }
}
