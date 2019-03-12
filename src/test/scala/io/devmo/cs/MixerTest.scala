package io.devmo.cs

import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}

import scala.concurrent.ExecutionContext

class MixerTest extends FunSuite with ScalaFutures {
  private implicit val ec: ExecutionContext = FlowExecutionContext(scala.concurrent.ExecutionContext.global)
  private implicit val pc: PatienceConfig = PatienceConfig(timeout = scaled(Span(15000, Millis)), interval = scaled(Span(100, Millis)))
  private val expected = FlowContext("test")

  FlowContextHolder.swap(expected)

  private val tested = new Mixer(new Blocking, new NonBlocking)
  test("fast should return true") {
    assert(tested.fast(expected).futureValue === true)
  }
  test("slow should return true") {
    assert(tested.slow(expected).futureValue === true)
  }
  test("medium should return true") {
    assert(tested.medium(expected).futureValue === true)
  }
  test("mix should return true") {
    assert(tested.mix(expected).futureValue === true)
  }
  test("randomFail should return true") {
    assert(tested.randomFail(expected).futureValue === true)
  }
  test("load should return true") {
    assert(tested.load(expected).futureValue === true)
  }
}
