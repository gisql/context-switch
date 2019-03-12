package io.devmo.cs

import java.util.concurrent.atomic.AtomicInteger

import io.devmo.cs.Utils.FtGen
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class UtilsTest extends FunSuite with ScalaFutures {
  private implicit val ec: ExecutionContext = FlowExecutionContext(scala.concurrent.ExecutionContext.global)
  private val expected = FlowContext("test")

  FlowContextHolder.swap(expected)

  test("multiply should invoke each future x times") {
    val a = new AtomicInteger()
    val b = new AtomicInteger()

    def inc(x: AtomicInteger): FtGen = ex => Future {
      x.incrementAndGet()
      FlowContextHolder.verify(ex)
    }

    whenReady(Utils.multiply(expected, 200, inc(a), inc(b))) { got =>
      assert(a.get === 200)
      assert(b.get === 200)
      assert(got === true)
    }
  }
}
