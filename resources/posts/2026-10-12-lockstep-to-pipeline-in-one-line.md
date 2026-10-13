title:  Lockstep to pipeline in almost one line

In the previous post [I talked about some of the benefits of a discrete time (tick) based system](https://andersmurphy.com/2026/10/05/discrete-time-based-systems-are-simple-to-benchmark.html). In this post I'm going to cover one of the other boons of a simple lockstep tick system. It's easy to change it into a pipelining tick system.

## Why did we start with lockstep?

Game loops are normally lockstep because it makes sense to do your updates and then render. It makes it easy to define your game (or app) as a sequence of phases which greatly simplifies reasoning about things like concurrency. Most of the time this is enough. However, even though [single writer systems can scale incredibly well](https://andersmurphy.com/2025/12/02/100000-tps-over-a-billion-rows-the-unreasonable-effectiveness-of-sqlite.html) you ideally want to be writing all the time. In a lockstep system you are by definition not writing when you are rendering. With a single writer that also means you only have one core doing work while you're writing.

## Pipelining

Pipelining is when you move phases to run in parallel instead of in sequence. Haven't we basically gone back to an event based continuous time system? No, not quite, the phases are still running on their own cores/threads in batches. They also cannot race ahead of each other, they all wait at a barrier before the next tick. This means we still have back pressure, and we can still track tick overruns as normal.

## What's a barrier?

From the Java docs:

> A synchronization aid that allows a set of threads to all wait for each other to reach a common barrier point. CyclicBarriers are useful in programs involving a fixed sized party of threads that must occasionally wait for each other. The barrier is called cyclic because it can be re-used after the waiting threads are released. If you need support for variable numbers of parties per cycle, alternate actions on exceptions, termination control, contention control, or status monitoring, use the more flexible Phaser class.

*Note: you know it's good when you start reaching for concurrency tools that sound like they are out of StarTrek (CyclicBarrier, Phaser and Disruptor)*

## Lockstep vs Pipeline

Lockstep tick model looks like this:

```
  READ ─▶ WRITE ─┐ 
   ▲             │ TICK
   └─────────────┘
```

Pipeline tick model looks like this:

``` 
  ┌──▶ READ ───┐ 
  │    WRITE ──┐
  │            │ TICK
  └────────────┘
```

With lockstep tick time is effectively:

```clojure
(max tick-time (+ write-time read-time))
```

So if `tick-time` was 100 and `write-time` was 90 and `read-time` was 100. A tick would take 190:

```clojure
(max 100 (+ 90 100)) ;; => 190
```

With pipeline tick time is effectively:

```clojure
(max tick-time write-time read-time)
```

So if `tick-time` was 100 and `write-time` was 90 and `read-time` was 100. A tick would take 100:

```clojure
(max 100 90 100) ;; => 100
```

With a pipeline read batch and write batches overlap. That's why they must be wrapped in transactions, so the reads don't see future writes until the next tick (or use the same basis-t if you're using a datomic-like).

But, you still have a tick barrier so that both must complete before the next tick. This gives you the same back pressure and the same overrun tracking as the lockstep model.

## Lockstep is evolving! Congratulations you now have a Pipelining!

Spoiler it was a 3 line diff:

```clojure
(fn batch-thread []
+ (.await ^CyclicBarrier start-barrier)
  (while (not (Thread/interrupted))
    (let [next-tick (+ (System/currentTimeMillis)
                      batch-tick-ms)
          batch     (ArrayList/new)]
      (.drainTo q batch)
      (batch-fn ctx (seq batch))
+     (.await ^CyclicBarrier done-barrier)
      (.addAll
        ^ConcurrentLinkedQueue
        render-queue ^Collection conns)
      (.await ^CyclicBarrier start-barrier)
-     (.await ^CyclicBarrier done-barrier)
      ...
      )))
```

Yeah, that's it. We move the `done-barrier` and kick it off with a `(.await ^CyclicBarrier start-barrier)` outside the while loop.

We open up 1000 SSE read connections to the server and keep them open. We then simulate a sequence of slow write transactions.

```clojure
(do (dotimes [_ 300]
        (tx! (fn [_ _] (Thread/sleep 90)))
        (Thread/sleep 101))
      (println "Done"))
```

In the lockstep system. This leads to lots of overruns.

```clojure
WARNING: tick overrun
{:n 76, :p50 100, :p95 987, :p99 1107, :max 1195}
```

The pipelining system is almost completely unaffected:

```clojure
WARNING: tick overrun
{:n 194, :p50 100, :p95 100, :p99 104, :max 105}
```

## Read consistency

We are now writing at the same time as reading. What about read consistency? Thankfully each of our render batches is wrapped in a read transaction, so the whole batch sees a consistent view. All the batches start before the write batch completes so there's no read skew (this can be strictly enforced by adding a third barrier if desired). For a given tick everyone is still seeing the same view of the database.

## Conclusion

Hopefully, this post shows you some of the benefits of modelling your system as separate phases coordinated around discrete time barriers.

Code [can be found here](https://github.com/andersmurphy/hyperlith).

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.
