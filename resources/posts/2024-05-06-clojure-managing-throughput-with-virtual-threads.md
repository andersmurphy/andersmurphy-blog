title: Clojure: managing throughput with virtual threads

Before the introduction of Java 21 virtual threads we were heavy users of [claypoole](https://github.com/clj-commons/claypoole) a fantastic library for simple but effective parallelism using thread pools. However, things get complicated with virtual threads, they shouldn't be pooled, as they aren't a scarce resource. So how do we limit throughput when we have "unlimited" threads? In this post we will look at using java `Semaphore` class to implement basic token bucket rate limiting to control throughput.

One of the other key insights I've drawn from claypoole is that unordered parallelism helps minimising latency, as it allows you to process results as soon as they became available. So we'll also explore unordered parallelism with virtual threads.

To get an virtual thread `executor` we can call:

```clojure
(defonce executor (Executors/newVirtualThreadPerTaskExecutor))
```

We can combine this with an `ExecutorCompletionService` to get tasks as they complete rather than the order they are submitted in:

```clojure
(def cs (ExecutorCompletionService/new executor))
```

We can submit tasks to the `CompletionService` with `.submit`:

```clojure
(run! (fn [x] (ExecutorCompletionService/.submit
                  cs #(inc x)))
    [1 2 3 4 5])
```

We are using Clojure 1.12.0-alpha10 methods as values syntax e.g: `ExecutorCompletionService/.submit`. This means we don't have to mannualy type hint to avoid reflection.

To take completed tasks from the service we use `.take`:

```clojure
(->> (repeatedly #(deref (ExecutorCompletionService/.take cs)))
    (take 5))
```

It's important to note that `.take` is blocking so if we `take` more tasks than there are from the completion service this code will block indefinitely (or until more tasks are submitted to the service). Because of this our implementation of `upmap` will consume eagerly (i.e it requires all of it's inputs before it will execute) as we need to know the total number of items that we will want to take `(count coll)`.

We can combine all of this to write our own implementation of `upmap` (unordered pmap):

```clojure
(defn upmap
  ([f coll]
   (let [cs (ExecutorCompletionService/new executor)]
     (run! (fn [x] (ExecutorCompletionService/.submit
                     cs #(f x))) coll)
     (->> (repeatedly #(deref (ExecutorCompletionService/.take cs)))
       (take (count coll))))))
```

`upmap` takes completed tasks lazily, which lets us process them as they are completed rather than waiting for all tasks to complete:

```clojure
(upmap inc [1 2 3 4 5 6])
=>

(2 3 4 5 6 7)
```

Now lets look at implementing rate limiting. There's no point spinning up a bunch of virtual threads to make requests against a third party API only to have some fail due to exceeding the third party API's rate limit.

We can use semaphores for this. Semaphores are similar to pools, but instead of pooling a scarce resource like threads, semaphores instead pool permits:

```clojure
(defonce sem
  (Semaphore/new 2 true))
```

We set fairness to `true` to prevent starvation. From the docs:

> The constructor for this class optionally accepts a fairness parameter... When fairness is set true, the semaphore guarantees that threads invoking any of the acquire methods are selected to obtain permits in the order in which their invocation of those methods was processed (first-in-first-out; FIFO).

Worth keeping in mind this will have a throughput cost, so might not always be the right choice:

> Generally, semaphores used to control resource access should be initialized as fair, to ensure that no thread is starved out from accessing a resource. When using semaphores for other kinds of synchronization control, the throughput advantages of non-fair ordering often outweigh fairness considerations.

We can combine Semaphores with virtual threads to implement token bucket rate limiting (X req/s with burst of X). This is trivial with virtual threads as we can spin up a new virtual thread (with `Thread/startVirtualThread`) to return the permit to the semaphore pool after the allotted time. In our case we have two permits and we want a rate limit of 2 req/s so we sleep for 1000ms before returning a permit:

```clojure
(defn rate-limited-sem-release [sem]
  ;; block until available
  (Semaphore/.acquire sem)
  ;; Create another virtual thread that will release this semaphore
  ;; to refill the bucked when the time is up.
  (Thread/startVirtualThread
    #(do (Thread/sleep 1000) ;; wait 1 second
         (Semaphore/.release sem))))
```

Something to keep in mind is the accuracy of `Thread/sleep`. From the java language specification:

> Thread.sleep causes the currently executing thread to sleep (temporarily cease execution) for the specified duration, subject to the precision and accuracy of system timers and schedulers. The thread does not lose ownership of any monitors, and resumption of execution will depend on scheduling and the availability of processors on which to execute the thread.

But for our use case this is accurate enough. In my testing it's only been off by a few milliseconds.

Combining this all together we get:

```clojure
(defn upmap
  ([f coll]
   (upmap nil f coll))
  ([sem f coll]
   (let [cs (ExecutorCompletionService/new executor)]
     (run!
       (fn [x]
         (when sem (rate-limited-sem-release sem))
         (ExecutorCompletionService/.submit cs (fn [] (f x)))) coll)
     (->> (repeatedly #(deref (ExecutorCompletionService/.take cs)))
       (take (count coll))))))
```

Let's see if it works:

```clojure
(time
  (->> (upmap sem inc [1 2 3 4 5 6 8 9 10])
    (run! prn)))
    
=>
3
4
5
10
11
7
6
9
"Elapsed time: 4019.565335 msecs"

nil
```

The execution time is correct (greater than 4000msec). However, we are getting all the results in one go. This is happening because the semaphore is blocking the `run!` function and the way the code is currently written we can't start taking from the `CompletionService` until all the tasks have been submitted.

We can get around this by throwing more virtual threads at the problem. We use `Thread/startVirtualThread` to execute the `run!` function in another thread, so even if it blocks we can still start taking completed tasks:

```clojure
(defn upmap
  ([f coll]
   (upmap nil f coll))
  ([sem f coll]
   (let [cs (ExecutorCompletionService/new executor)]
+    (Thread/startVirtualThread
       #(run!
          (fn [x]
            (when sem (rate-limited-sem-release sem))
            (ExecutorCompletionService/.submit cs (fn [] (f x)))) coll))
     (->> (repeatedly #(deref (ExecutorCompletionService/.take cs)))
       (take (count coll))))))
```

Let's see if `upmap` now behaves as we expect:

```clojure
(time
  (->> (upmap sem inc [1 2 3 4 5 6 8 9 10])
    (run! prn)))
    
=>
3
4
...
5
10
...
11
7
...
6
9
"Elapsed time: 4019.565335 msecs"

nil
```

Excellent we now get the results as they complete, whilst still respecting the rate limit. 

All of this with zero dependencies. The power that Clojure's tight integration with the Java is really amazing.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/virtual-threads/managing-throughput).

Further reading:

- [Managing Throughput with Virtual Threads - Sip of Java](https://inside.java/2024/02/04/sip094/ )
- [Virtual Threads in Clojure](https://ericnormand.me/guide/clojure-virtual-threads)
- [Clojure Method Values](https://clojure.org/news/2024/04/28/clojure-1-12-alpha10#method_values)
- [ExecutorCompletionService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html)
- [Semaphore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html)
