title:  Discrete time based systems are simple to benchmark

In the previous post [I briefly discussed a tick based batching system for streaming HTML](https://andersmurphy.com/2026/09/29/faster-streaming-html-with-batches-and-aggregates.html). In this post I'm going to dive deeper in one of the many benefits of a discrete time (tick) based system is simplified profiling, benchmarking and load testing.

## Non-Uniform Work Distributions

In the previous post we covered reducing contention by sharing less between threads. This generally involves threads having their own resources (buffers, caches, database connection). This is powerful, but assumes each render task takes the same amount of work which is not always the true. In this app connections are long lived which means that over time (as users disconnect) we can end up with some render threads having more connections to render for than others.

On the one hand different pages render at different speeds based on database queries and the amount of templating involved. But, also based on what's cached (if you have a shared cache).

On the other hand the share nothing model is great for local caches (including database connection caches). As it keeps connections on the same thread. The contention reduction is also incredibly important when you have many cores.

In this app the template fragment cache is shared and we are using SQLite with mmap, so the database page cache is also shared.

So how would we go about comparing both these approaches?

## Overrun logging

With a tick based system it's simple to add overrun logging (when a tick overruns):

```clojure
(let [sleep-time-ms
      (- next-tick (System/currentTimeMillis))]
  (when (< sleep-time-ms 0)
    ;; log overrun
    (when (= @overruns [])
      (Thread/startVirtualThread
        (fn [] (Thread/sleep 20000)
          (println "WARNING: tick overrun")
          (println (u/stats @overruns))
          (reset! overruns []))))
    (swap! overruns conj
      (- batch-tick-ms sleep-time-ms)))
  (when (> sleep-time-ms 0)
    (when (not (= @overruns []))
      (swap! overruns conj batch-tick-ms))
    (Thread/sleep ^long sleep-time-ms)))
```

When a tick overruns we start recording tick times and print out a sample:

```clojure
WARNING: tick overrun
{:n 179, :p50 100, :p95 146, :p99 298, :max 453}

```

Here we see the total number of ticks in the sample, as well as p50, p95, p99 and max. With this in place our system become easy to benchmark as a whole. We can run a load test and watch the logs.

## Quick primer on load testing

Generally, I prefer load testing benchmarks over micro benchmarks. This is because it tests the system as a whole. A micro benchmark of a templating engine might be fast when rendering a single template. However, in practice your server will be rendering hundreds or thousands of templates simultaneously. Your implementation might be fast in isolation because it generates a load of garbage, trading allocations for speed (which is fine in isolation). However, in the context of hundreds of renders happening simultaneously those allocations grind the system to a halt.

The other rule of load testing, is do not load test with an external process unless that external process is on another machine. This one is really important. As your load tests get more extreme, the load test process itself starts eating into your app resources and introducing noise into your tests.

I have a babashka script for load testing. Great for testing a VPS from another machine. But, on the same machine it's CPU usage can easily be robbing the system of a core or two. There's a deeper problem though. If your system supports back pressure the test script falling behind reduces the amount of work your system is doing.

## Local load testing

All that being said, it's nice to load test on your local machine, especially for flamegraphs and other types of profiling (you should almost always profile in production too if you can).

In a tick based system this is actually quite simple. Even more so in our setup where we do a render for all users every tick (regardless of any change). We can simulate read load simply by opening connections. To test the amount of connections we want to test though an external script we would still need to do a lot of work consuming the delivered bytes (even if it's just dumps them).

Because of how our system is designed with back pressure we need those bytes to be consumed as the system will skip renders for slow clients. While this is great in production as it prevents a slow client slowing down the system. It can lead to misleading benchmark results.

To get around this we are going to use our system without the http server. This removes back pressure, but also eliminates the overhead of an external load testing script. We just add connections and let the system do its thing. Those connections don't actually go anywhere. Effectively simulating the entire system minus the last step (network transfer).

In the case of [Hyperlith](https://github.com/andersmurphy/hyperlith). This can be done by starting the app in "headless" test mode.

```clojure
(def stub-router
  (do (start-app! :mode :test)
    (@app_ :wrapped-router)))
```

## The render queue

Ok, so back to the actual problem. We want to test a task queue based approach. In this model we have a single `ConcurrentHashMap` that holds all our connections (rather than it being local to the render thread). We take a view of the current connections and put them on a `ConcurrentLinkedQueue`.

```clojure
(let [next-tick (+ (System/currentTimeMillis)
                  batch-tick-ms)
      batch     (ArrayList/new)]
  (.drainTo q batch)
  (batch-fn ctx (seq batch))

+  (.addAll
+    ^ConcurrentLinkedQueue
+    render-queue
+    ^Collection
+    (.values ^ConcurrentHashMap conns))

  ;; Start renders
  (.await ^CyclicBarrier start-barrier)
  ;; Wait until all renders complete
  (.await ^CyclicBarrier done-barrier)

  ...

  )
```

The reason we are using a `ConcurrentLinkedQueue` is because we can poll without blocking. This fits well with our model where we drain the queue until it's empty.

*Note: We could use a LMAX disruptor style ring buffer for even more speed (I might revisit ring buffers in a future post). But, for now a ConcurrentlLinkedQueue is fast enough.*

```clojure
(let [cache (cache/init
{:max-weight fragment-cache-size
:weigher    (fn [_k ^bytes v] (alength v))})]
(->> (range render-pool-size)
(mapv (fn [_]
(let [lane-ctx ^LaneCtx
(lc/new-lane-ctx
{:dbs            (sqlite/create-read-connections! dbs)
:html-dst-buf   (ByteBuffer/allocateDirect
render-buffer-size)
:fragment-cache cache})]
(-> (Thread.
^Runnable
(bound-fn* ;; binding conveyance
(fn render-thread []
(while (not (Thread/interrupted))
                          (.await ^CyclicBarrier start-barrier)
                          (run! sqlite/start-read-tx
                            (vals (.dbs lane-ctx)))

+                          (u/while-some
+                            [render (.poll
+                                      ^ConcurrentLinkedQueue
+                                      render-queue)]
+                            (render lane-ctx))

                          (run! sqlite/end-read-tx
                            (vals (.dbs lane-ctx)))
                          (.await ^CyclicBarrier done-barrier)))))
                Thread/.start)
              lane-ctx)))))
```

To test this approach we are going to create 12000 long lived connections. Every 4th connection will be to a page that is fast to render (does almost no work). The prior version of our system used round robin render thread assignment this results in a render thread having only easy work and the other three threads having regular work (in a four core system).

```clojure
(dotimes [i 12000]
  (if (zero? (mod i 4))
    (stub-router
      {:request-method :post
       :uri            "/no-work"
       :headers        {"content-type"    "application/json"
                        "accept-encoding" "zstd"
                        "sec-fetch-site"  "same-origin"
                        "cookie"          (str "__Host-sid=" "test-user-" i)}
       :body           (h/edn->json {"tabid" "7dc673ca"})})
    (stub-router
      {:request-method :post
       :uri            "/"
       :headers        {"content-type"    "application/json"
                        "accept-encoding" "zstd"
                        "sec-fetch-site"  "same-origin"
                        "cookie"          (str "__Host-sid=" "test-user-" i)}
       :body           (h/edn->json {"tabid" "7dc673ca"})})))
```

Once the connections are set up we can just watch our logs and see the numbers come in.

Unbalanced work with round robin (pre-assigned render thread at connection time):

```
{:n 31, :p50 661, :p95 717, :p99 721, :max 728}
{:n 31, :p50 664, :p95 726, :p99 734, :max 771}
{:n 30, :p50 666, :p95 727, :p99 759, :max 762}
```

Shared task queue (fast threads grab more tasks):

```
{:n 55, :p50 361, :p95 454, :p99 482, :max 508}
{:n 53, :p50 374, :p95 451, :p99 469, :max 479}
{:n 54, :p50 373, :p95 468, :p99 474, :max 584}
```

About 43% faster. Obviously this is an extreme example. But, we are looking for consistent tail latency and we are not using thread local caches that benefit from a render happening on the same thread each tick. The core count is also low, so contention is less of a concern. The numbers might be completely different on a 96 core machine.

## Why not just use a ThreadPoolExecutor?

In a lot of cases you could just use an `ThreadPoolExecutor`, with a custom thread factory for thread relevant context, and call `invokeAll`. However, executors don't let you run batch specific code. In this app it's important we wrap each batch for each thread with a SQLite read transaction. Because each query is it's own implicit transaction at 12000 connections we are doing 3,000,000 queries per second (each render is 25 queries). Without the wrapped read transaction that means acquiring a lock on the WAL file 3 million times a second. By wrapping the whole batch, we reduce that to 1 lock per core per tick.

## Conclusion

Hopefully, this post helps show you how discrete time based systems dramatically simplify benchmarking.

Code [can be found here](https://github.com/andersmurphy/hyperlith).

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.
