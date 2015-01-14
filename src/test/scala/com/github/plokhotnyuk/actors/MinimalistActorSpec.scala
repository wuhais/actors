package com.github.plokhotnyuk.actors

import java.util.concurrent.CountDownLatch

import com.github.gist.viktorklang.Actor
import com.github.plokhotnyuk.actors.BenchmarkSpec._
import org.specs2.execute.Success
import com.github.gist.viktorklang.Actor._

class MinimalistActorSpec extends BenchmarkSpec {
  implicit val executorService = createExecutorService()

  "Enqueueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    footprintedAndTimed(n) {
      sendMessages(a, n)
    }
    l1.countDown()
    l2.await()
    Success()
  }

  "Dequeueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    sendMessages(a, n)
    timed(n) {
      l1.countDown()
      l2.await()
    }
    Success()
  }

  "Initiation" in {
    footprintedAndTimedCollect(1000000)(() => Actor(_ => _ => Stay))
    Success()
  }

  "Single-producer sending" in {
    val n = 15000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
    Success()
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(15000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
    Success()
  }

  "Max throughput" in {
    val n = roundToParallelism(30000000)
    val l = new CountDownLatch(parallelism)
    val r = new ParRunner((1 to parallelism).map {
      _ =>
        val a = countActor(l, n / parallelism)
        () => sendMessages(a, n / parallelism)
    })
    timed(n) {
      r.start()
      l.await()
    }
    Success()
  }

  "Ping latency" in {
    ping(3000000, 1)
    Success()
  }

  "Ping throughput 10K" in {
    ping(6000000, 10000)
    Success()
  }

  def shutdown(): Unit = fullShutdown(executorService)

  def actor(f: Any => Unit) = Actor(_ => m => {
    f(m)
    Stay
  }, batch = 1024)

  private def ping(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor.Address = null
        val a2 = actor {
          var i = n / p / 2
          (m: Any) =>
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a1 = actor {
          var i = n / p / 2
          (m: Any) =>
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a2
    }
    timed(n, printAvgLatency = p == 1) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor.Address =
    actor {
      var blocked = true
      var i = n - 1
      (_: Any) =>
        if (blocked) {
          l1.await()
          blocked = false
        } else {
          i -= 1
          if (i == 0) l2.countDown()
        }
    }

  private def countActor(l: CountDownLatch, n: Int): Actor.Address =
    actor {
      var i = n
      (_: Any) =>
        i -= 1
        if (i == 0) l.countDown()
    }

  protected def sendMessages(a: Actor.Address, n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}