package io.devmo.cs

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success, Try}

class StreamService(queueSize: Int)(implicit ec: ExecutionContext, mat: Materializer) extends Service {
  private case class Req(ctx: FlowContext, delayMs: Int, fail: Boolean = false)
  private val queue: SourceQueue[(Req, Promise[Boolean])] = {
    def doStuff[T](in: (Req, T)): (Try[Boolean], T) = in match {
      case (req, rv) =>
        Thread.sleep(req.delayMs)
        if (req.fail) Failure(new RuntimeException()) -> rv
        else Success(FlowContextHolder.verify(req.ctx)) -> rv
    }

    val pipe: Flow[(Req, Promise[Boolean]), (Try[Boolean], Promise[Boolean]), NotUsed] = Flow.fromFunction(doStuff[Promise[Boolean]])

    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val in = Inlet[(Req, Promise[Boolean])]("in")
      val bcast = b.add(Broadcast[(Req, Promise[Boolean])](2))
      val merge = b.add(Merge[(Try[Boolean], Promise[Boolean])](2))
      bcast ~> pipe ~> merge
      bcast ~> pipe ~> merge

      ???
    })
    val x: FlowShape[Int, String] = FlowShape.of(Inlet[Int](""), Outlet[String](""))
    val xx: Graph[FlowShape[Int, String], NotUsed] = GraphDSL.create() { implicit b =>
      x
    }
    Flow.fromGraph(xx)
    GraphDSL.create() { implicit b =>


      val in = Inlet[(Req, Promise[Boolean])]("in")
      val out = Outlet[(Try[Boolean], Promise[Boolean])]("out")

      val bcast = b.add(Broadcast[(Req, Promise[Boolean])](2))
      val merge = b.add(Merge[(Try[Boolean], Promise[Boolean])](2))

      in ~> bcast // ~> pipe ~> merge ~> out

      ???
    }

    Source.queue[(Req, Promise[Boolean])](queueSize, OverflowStrategy.backpressure)
      .via(pipe)
      .toMat(Sink.foreach({
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p) => p.failure(e)
      }))(Keep.left)
      .run()
  }

  private def execute(req: Req): Future[Boolean] = {
    val responsePromise = Promise[Boolean]()
    queue.offer(req -> responsePromise) flatMap {
      case QueueOfferResult.Enqueued => responsePromise.future
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
