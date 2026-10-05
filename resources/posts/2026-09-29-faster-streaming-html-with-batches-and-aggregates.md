title:  Faster streaming HTML with batches and aggregates

In this post I'm going to dive into some of the fun emergent properties of server based streaming HTML. In some ways this is a long overdue follow up of this post: [Realtime collaborative web apps without ClojureScript](https://andersmurphy.com/2025/04/07/clojure-realtime-collaborative-web-apps-without-clojurescript.html).

## Quick overview of this architecture

In this model almost all state is on the server. We stream the next frame (when I say frame I mean the next version of the html page generated on the server) to every connected client every X ms (a tick). This style of rendering is often referred to as immediate mode (fat morph in the [Datastar discord](https://discord.com/invite/bnRNgZjgPh)). These frames are streamed to each client over a long lived SSE connection with streaming compression (Brotli or Zstandard).

Think `view = f(state)` just on the server rather than the client.

## Bounding the system with ticks

Ticks are awesome because they give you a boundary to batch against, back pressure and a place to measure the performance of your system. If your server is under load frames are dropped, but it doesn't fall over.

Without this batching your system can easily be accidentally quadratic. X users do Y actions each action triggers a render for each user. So 1000 users each doing 1 actions a second is 1000 x 1000 = 1000000 renders. In a 1 second tick based system (updates every second) that would only be 1000 renders regardless of how many actions the users perform.

Example of a simple tick based game loop:

```clojure
(while (not (Thread/interrupted))
  (let [next-tick (+ (System/currentTimeMillis) tick-ms)]

    ;; Do work ...

    (let [sleep-time-ms
          (- next-tick (System/currentTimeMillis))]
      (when (< sleep-time-ms 0)
        ;; log overrun
        ...)
      (when (> sleep-time-ms 0)
        (Thread/sleep ^long sleep-time-ms)))))
```

## Barriers

When you have a tick based system you have a natural point to introduce a barrier. For example the simplest way to batch writes is to have a single writer. In a tick based system you can have a clear barrier between your writes and your reads. 

Batch your writes -> batch your renders -> ... 

This lets you batch your renders. This can be as simple as iterating over all your long lived connections and generating the HTML they need to render.  Or if you want get slightly fancier pinning each connection group to a core and iterating over them. This allows each group to have their own thread local resources (buffers, caches, database connection). Which can be great for making your system more deterministic and bounding memory usage. 

Before you might have had a 64kb buffer for each connection's template generation, but with batched renders, that ends up being 64kb per batching thread. Say you have 10000 concurrent users, that would be 640mb. Even worse if you're not using buffer, you'd be generating 640mb of garbage every render. With the batching model you'd have 64kb per thread, so in a 4 core system that would be a minuscule 256 kb!

In the case of database connections (and sometimes caches) you eliminate contention ([see this LMAX talk for more on why this is important](https://www.youtube.com/watch?v=qDhTjE0XmkE)) . Each render thread has it's own resources so there's no need for coordination.

Example barrier in tick based game loop:

```clojure
(while (not (Thread/interrupted))
  (let [next-tick (+ (System/currentTimeMillis) tick-ms)]
    ;; Do writes

    ;; Start renders
    (.await ^CyclicBarrier start-barrier)
    ;; Wait until all renders complete
    (.await ^CyclicBarrier done-barrier)

    (let [sleep-time-ms
          (- next-tick (System/currentTimeMillis))]
      (when (< sleep-time-ms 0)
        ;; log overrun
        ...)
      (when (> sleep-time-ms 0)
        (Thread/sleep ^long sleep-time-ms)))))
```

Render threads would look something like this:

```clojure
(Thread.
  ^Runnable
  (fn render-thread []
    (while (not (Thread/interrupted))
      (.await ^CyclicBarrier start-barrier)
      ;; Render work happens here ...
      (.await ^CyclicBarrier done-barrier))))
```

## Compression and bandwidth 

[In another post](https://andersmurphy.com/2025/04/15/why-you-should-use-brotli-sse.html) I covered how streaming compression eliminates the network overhead of immediate mode rendering. It's ok to send the whole 50kb HTML frame because on the wire it will be as small as 13bytes (if nothing changed).

## Querying the database every tick?

But doesn't a tick based model involve querying the database every tick? In my case yes, yes it does. But, if you're using an embedded database like SQLite for projections then your projections are laid out so that they are quick to query. If you're worried about write throughput you should check out this post [100000 TPS over a billion rows: the unreasonable effectiveness of SQLite](https://andersmurphy.com/2025/12/02/100000-tps-over-a-billion-rows-the-unreasonable-effectiveness-of-sqlite.html).

## HTML templating

DEEP BREATH. We are breathing rare air here.  String generation, concatenation, encoding, escaping and some iterating are our bottlenecks.

So with ticks, barriers, compression and sqlite we've eliminated a load of work. Well that leaves one last bottleneck. With thousands of concurrent users being updated 10 times a second, HTML templating ends up occupying a lot of our frame budget. 

You could do something clever like only update users who need to be updated. But, that doesn't solve all users having a shared widget and all needing to be updated anyway. Or dynamic content that changes all the time etc. The goal here is to not be accidentally quadratic. Any optimisation that helps the happy path but doesn't improve our worse case is just overhead when things go wrong.

This is where aggregates come in. Because, we've deliberately not been clever up to this point: broadcast to all users on every tick even if nothing changes. We are in a position to think about rendering as a batch process that happens for all users. Which means we can assume that there is likely to be some overlap between all the frames in a batch, but also all the frames in the previous batches.

But first...

![the night is long and full of flamegraphs](/assets/9ggtzb.gif)

*CPU Flamegraph of a concurrent user overload test. 4000 users all looking at slightly different views without caching. You can use the search feature to find things like sqlite etc.*

<center>
<iframe src="/assets/20260929_213013-02-cpu-flamegraph.html" style="height:600px; width:100%;"></iframe>
</center>

## Simple hiccup interpreter

Our system has a simple hiccup interpreter that recursively iterates over hiccup and writes to a byte buffer:

```clojure
[[:h1 "One Billion Checkboxes"]
 [:p "Built using "
  [:a {:href "https://clojure.org/"} "Clojure"]
  " and "
  [:a {:href "https://data-star.dev"} "Datastar"]
  " - "
  " - "
  [:a {:href "https://andersmurphy.com/about"} "blog"]]]
```

There's two small changes with this hiccup interpreter. If it encounters a function it will eval it and assume the output is more hiccup to be interpreted. 

```clojure
(defn write-node
  [lane-ctx node ^ByteBuffer out]
  (cond
    (nil? node) nil

    (bytes? node) (.put out ^bytes node)

    (string? node)
    (write-escaped-string node out)

    (instance? Sequential node)
    (write-collection lane-ctx out node)

     ;; if function we evaluate it
    (fn? node)
    (write-node lane-ctx (node) out)

    :else (write-escaped-string (str node) out)))
```

The main benefit of this is you can delay work until the interpreter reaches that point. So your database queries can be streaming straight into your output byte buffer without materialising the full result of the query.

If it encounters an element who's first argument is a function it will apply the rest of the elements content to that function as arguments (think of them as components). 

```clojure
(fn write-collection
  [^LaneCtx lane-ctx ^ByteBuffer out ^Iterable collection]
  (let [^Iterator iterator (.iterator collection)]
    (when (.hasNext iterator)
      (let [item (.next iterator)]
        (cond  (keyword? item)
               (write-element lane-ctx out iterator item)

               (fn? item)
               (let [cache (.fragment-cache lane-ctx)
                           b     (or (cache/get cache collection)
                                   (cache/put cache collection
                                     (html->bytes (apply item
                                                    (subvec collection 1))
                                       lane-ctx)))]
                       (.put out ^bytes b))

               :else
               (do (write-node lane-ctx item out)
                   (while (.hasNext iterator)
                     (write-node lane-ctx (.next iterator) out))))))))
```

What's cool is we can wrap these "components" in a cache and key their output by their function and arguments. Effectively this is automatic content addressable caching. Components are just function:

```clojure
(defn Palette [current-selected]
  [:div {:class "palette"}
   (mapv (fn [state]
           [:div
            {:data-id     state
             :data-action handler-palette
             :data-color  state
             :class
             ["palette-item" (when (= current-selected state)
                               "palette-selected")]}])
     (subvec states 1))])
```

And we can reference them in hiccup similar to components in Reagent (although I might change this to chassis style aliases in future):

```clojure
[:div
 {:class     "controls-wrapper"
  :data-init (scroll-to-xy-js init-jump-x init-jump-y)}
 [Jump]
 [Palette (or (:color tab-data) 1)]
 [Info]]
```

They can be nested just fine, because our interpreter is recursive. 

## Cache thrash 

But, what about thrashing the cache? We've got automatic component level content addressable caching. A bunch of small components that are all different could push out our more valuable cache entries!

This is where we lean on [WTinyLFU](https://github.com/ben-manes/caffeine/wiki/Efficiency). WTinyLFU is a really cool caching algorithm implemented by the [caffeine caching library](https://github.com/ben-manes/caffeine).

It has two really cool features. Entries are only admitted if they are seen with a certain frequency. This means all those small entries that are all different: they never make it in.

But, the other interesting property is entries that are not requested often naturally drop out of the cache.

This leads to really interesting emergent optimisation behaviour. Say we have a nested components. They present a problem. If we cache all the children and the parent (including all the children). We have roughly doubled the amount of space we take in the cache. But, with WTinyLFU if the wrapping component always changes, it never gets cached cause of the admission mechanism. If the wrapper component is stable and sticks around all the cached sub components drop out of the cache because they are never
requested.

*CPU Flamegraph of a concurrent user overload test. 4000 users all looking at slightly different views after content addressable component caching was added.*

<center>
<iframe src="/assets/20260929_130031-01-cpu-flamegraph.html" style="height:600px; width:100%;"></iframe>
</center>

## Conclusion

Thinking in batches and aggregates lets you do really powerful performance optimisation whilst keeping your system simple to reason about. When doing app development I don't have to think about caching or performance. I can just write hiccup and sql queries.

You can check out the experimental [source code for this project at the hyperlith repo](https://github.com/andersmurphy/hyperlith). It's [running in production here](https://checkboxes.andersmurphy.com/) and handles roughly 1000 concurrent users at 10 FPS on a 2 vCore shared VPS.

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.

Flamegraphs were made with Oleksandr Yakushev incredible [clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler).
