package io.devmo.cs

import java.util.UUID
import java.util.concurrent._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.Random

class MultiContextTest extends FunSuite with ScalaFutures {
  private implicit val pc: PatienceConfig = PatienceConfig(timeout = scaled(Span(150000, Millis)), interval = scaled(Span(100, Millis)))
  private val rawActors = ActorSystem()
  private val mat: Materializer = FlowMaterialiser(ActorMaterializer()(rawActors))
  private val flowedActors = ActorSystem("proper-rest", None, None, Some(FlowExecutionContext(ExecutionContext.fromExecutor(new ForkJoinPool(16)))))

  private val ecs = Map[String, ExecutionContext](
    "global" -> scala.concurrent.ExecutionContext.global,
    "fork-join" -> ExecutionContext.fromExecutor(new ForkJoinPool(16)),
    "thread-pool" -> ExecutionContext.fromExecutor(new ThreadPoolExecutor(4, 16, 10, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](2000))),
    "same-thread" -> ExecutionContext.fromExecutor(SameThreadExecutor),
    "double-wrapped" -> new Wrapper(FlowExecutionContext(ExecutionContext.fromExecutor(new ForkJoinPool(16)))),
    "raw-actors" -> rawActors.dispatcher,
    "flowed-actors" -> flowedActors.dispatcher
  ).mapValues(FlowExecutionContext.apply)

  ecs.keys foreach { name =>
    test(s"the same executor for all: $name") {
      implicit val ec: ExecutionContext = ecs(name)

      val tester = new Tester
      val httpLike = new StreamService(20000, 100)(implicitly[ExecutionContext], mat)
      val mixer = new Mixer(new Blocking, new NonBlocking, new PatternService(rawActors), httpLike)
      assert(tester.load(20, mixer.fast, mixer.mix, mixer.medium).futureValue === true)
    }
  }
  private def sample(n: Int): List[String] = {
    val keys = ecs.keys.toArray
    (1 to n).map(_ => keys(Random.nextInt(keys.length))).toList
  }

  (1 to 30).map(_ => sample(6)).distinct foreach { case List(main, block, nBlock, mix, actors, streams, _*) =>
    test(s"main: $main, block: $block, non-block: $nBlock, mix: $mix, streams: $streams") {
      val tester = new Tester()(ecs(main))
      val blocking = new Blocking()(ecs(block))
      val nonBlocking = new NonBlocking()(ecs(nBlock))
      val patterns = new PatternService(flowedActors)(ecs(actors))
      val httpLike = new StreamService(20000, 100)(ecs(streams), mat)
      val mixer = new Mixer(blocking, nonBlocking, patterns, httpLike)(ecs(mix))
      assert(tester.load(20, mixer.fast, mixer.mix, blocking.fast, blocking.medium, nonBlocking.fast).futureValue === true)
    }
  }

  private object SameThreadExecutor extends Executor {
    override def execute(command: Runnable): Unit = command.run()
  }
  private class Wrapper(target: ExecutionContextExecutor) extends ExecutionContextExecutor {
    override def execute(command: Runnable): Unit = target.execute(command)
    override def reportFailure(cause: Throwable): Unit = target.reportFailure(cause)
  }
}

class Tester(implicit val ec: ExecutionContext) {
  def load(times: Int, xs: Utils.FtGen*): Future[Boolean] = {
    def req = Future {
      val ctx = FlowContext(UUID.randomUUID.toString)
      FlowContextHolder.swap(ctx)
      Utils.sequencer(ctx, xs: _*)
    }.flatten

    Future.traverse((1 to times).toList)(_ => req).map(_.forall(identity))
  }
}
