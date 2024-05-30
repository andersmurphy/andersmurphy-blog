title: Clojure: virtual thread and dynamic var performance

In the previous post we explored [structured concurrency, scoped values and binding conveyance](https://andersmurphy.com/2024/05/14/clojure-structured-concurrency-and-scoped-values.html). In this post we'll explore the performance limitations of dynamic var in the context of virtual threads and why scoped values exist.

A quick disclaimer. I'm using [criterium quick-bench](https://github.com/hugoduncan/criterium) to compare the execution speed of the three implementations below. This is an easy way to benchmark functions. However, benchmarks are very context dependent and these test are by no means extensive. So take these results with a grain of salt and always do you're own benchmarking.

For an extensive guide to benchmarking and performance tuning, I highly recommend you check out [Clojure Goes Fast](https://clojure-goes-fast.com/) by Oleksandr Yakushev, it's an incredible resource.

## Set up code

Let's define a `pmap` function that we will be using in the test and a convenience macro `scoped-binding` for using scoped values:

```clojure
(ns app.core
  (:require [criterium.core :as crit])
  (:refer-clojure :exclude [pmap])
  (:import
   (java.lang ScopedValue)
   (java.util.concurrent
     StructuredTaskScope
     StructuredTaskScope$Subtask
     StructuredTaskScope$ShutdownOnFailure)))

(defn pmap [f coll]
    (with-open [scope (StructuredTaskScope$ShutdownOnFailure/new)]
      (let [r (mapv (fn [x]
                      (StructuredTaskScope/.fork scope
                        (fn [] (f x))))
                coll)]
        ;; join subtasks and propagate errors
        (.. scope join throwIfFailed)
        ;; fork returns a Subtask/Supplier not a future
        (mapv StructuredTaskScope$Subtask/.get r))))

(defmacro scoped-binding [bindings & body]
  (assert (vector? bindings)
    "a vector for its binding")
  (assert (even? (count bindings))
    "an even number of forms in binding vector")
  `(.. ~@(->> (partition 2 bindings)
           (map (fn [[k v]]
                  (assert (-> k resolve deref type (= ScopedValue))
                    (str k " is not a ScopedValue"))
                  `(ScopedValue/where ~k ~v))))
     (ScopedValue/get (delay ~@body))))
```

## Context

Next we need some context:

```clojure
(def context-data
  {:increase 1
   :colors   {:red 1 :blue 2 :green 3}
   :a        "A bunch of context stuff"
   :b        "B bunch of context stuff"
   :c        "C bunch of context stuff"})
```

## Dynamic vars

First we are going to test Clojure's dynamic vars:

```clojure
(def ^:dynamic *context* nil)

(crit/quick-bench
  (binding [*context* context-data]
    (pmap (bound-fn*
            (fn [x]
              (let [result (+ x (:increase *context*))]
                result)))
      (repeat 500000 1))))
      
;; Evaluation count : 6 in 6 samples of 1 calls.
;;            Execution time mean : 1.196520 sec
;;   Execution time std-deviation : 41.818110 ms
;;  Execution time lower quantile : 1.143932 sec ( 2.5%)
;;  Execution time upper quantile : 1.243698 sec (97.5%)
;;                  Overhead used : 1.845282 ns
```

With 50000 virtual threads `pmap`'s execution time mean is 1.196520 sec (on my 2020 MacBook - Pro 2GHz Quad-Core Intel Core i5).

## Scoped Values

Next scoped values:

```clojure
(crit/quick-bench
  (scoped-binding [scoped-context  context-data]
    (pmap (fn [x]
            (let [result (+ x
                           (:increase (ScopedValue/.get scoped-context)))]
              result))
      (repeat 500000 1))))

;; Evaluation count : 6 in 6 samples of 1 calls.
;;            Execution time mean : 197.549698 ms
;;   Execution time std-deviation : 24.230133 ms
;;  Execution time lower quantile : 172.035517 ms ( 2.5%)
;;  Execution time upper quantile : 220.525422 ms (97.5%)
;;                  Overhead used : 1.845282 ns
```

With 50000 virtual threads `pmap`'s execution time mean is 197.549698 ms. Roughly, 5x faster than the dynamic var version.

Why are dynamic vars so much slower with large numbers of virtual threads? Under the hood [clojure dynamic vars use ThreadLocal](https://github.com/clojure/clojure/blob/c07c39cac49a91f6031fe05c2eb7a257aa089176/src/jvm/clojure/lang/Var.java#L71).  ThreadLocal has performance implications for virtual threads due to expensive inheritance:

> Expensive inheritance — The overhead of thread-local variables may be worse when using large numbers of threads, because thread-local variables of a parent thread can be inherited by child threads. (A thread-local variable is not, in fact, local to one thread.) When a developer chooses to create a child thread that inherits thread-local variables, the child thread has to allocate storage for every thread-local variable previously written in the parent thread. This can add significant memory footprint. Child threads cannot share the storage used by the parent thread because the ThreadLocal API requires that changing a thread's copy of the thread-local variable is not seen in other threads. This is unfortunate, because in practice child threads rarely call the set method on their inherited thread-local variables.
>
> - JEP 446

## Explicit argument

It's worth looking at why dynamic vars and scoped values exist. The summary from JEP 446 explains it well:

>In effect, a scoped value is an implicit method parameter. It is "as if" every method in a sequence of calls has an additional, invisible, parameter. None of the methods declare this parameter and only the methods that have access to the scoped value object can access its value (the data). Scoped values make it possible to pass data securely from a caller to a faraway callee through a sequence of intermediate methods that do not declare a parameter for the data and have no access to the data.
>
> - JEP 446

If your function call stack is shallow, it can be simpler to pass the context as an explicit argument.

```clojure
(crit/quick-bench
  (let [context context-data]
    (pmap (fn [x]
            (let [result (+ x
                           (:increase context))]
              result))
      (repeat 500000 1))))

;; Evaluation count : 6 in 6 samples of 1 calls.
;;            Execution time mean : 189.313904 ms
;;   Execution time std-deviation : 22.768815 ms
;;  Execution time lower quantile : 165.292587 ms ( 2.5%)
;;  Execution time upper quantile : 215.966794 ms (97.5%)
;;                  Overhead used : 1.845282 ns
```

With 50000 virtual threads `pmap`'s execution time mean is 189.313904 ms. Roughly, 5x faster than the dynamic var version and a similar speed to the scoped values version.

My takeaway is: you probably don't need dynamic scope. Instead, pass the context explicitly. If you really think you need dynamic scope and are using a large number of virtual threads, favour scoped values over dynamic vars. That being said, thread conveyance for scoped values only works in the context of structured concurrency, so if you are using things like `Executor` and `CompletionService` you'll have to either pass context explicitly or use dynamic vars.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/virtual-threads/dynamic-var-perf).

**Further Reading:**

- [JEP 446: Scoped Values](https://openjdk.org/jeps/446)
- [JEP 462: Structured Concurrency](https://openjdk.org/jeps/462)
- [Clojure: managing throughput with virtual threads](https://andersmurphy.com/2024/05/06/clojure-managing-throughput-with-virtual-threads.html)
- [Clojure: structured concurrency and scoped values](https://andersmurphy.com/2024/05/14/clojure-structured-concurrency-and-scoped-values.html)
