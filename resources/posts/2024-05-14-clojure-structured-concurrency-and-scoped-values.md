title: Clojure: structured concurrency and scoped values

In this post we'll explore some useful tools for making working with virtual threads easier. Structured concurrency helps eliminate common problems like thread leaks and cancellation delays. Scoped values let you extend parent thread based context to child threads so you can treat a group of threads as a single unit of work with the same shared context.

## Enable preview

Structured concurrency and scoped values are available in java 21 as preview features, so we'll need to enable preview:

```clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.12.0-alpha11"}}
 :aliases
{:dev {:jvm-opts ["--enable-preview"]}}}
```

## Example code

We'll be implementing our own version of `pmap` as it has a clear thread hierarchy which is exactly the sort of place both structured concurrency and scoped values are useful:

```clojure
(ns server.core
  (:refer-clojure :exclude [pmap])
  (:import
   (java.util.concurrent
     ExecutorService
     Executors
     Callable)))

(defonce executor
  (Executors/newVirtualThreadPerTaskExecutor))

(defn pmap [f coll]
  (->> (mapv (fn [x] (ExecutorService/.submit executor
                       ;; More than one matching method found: submit
                       ;; So we need to type hint Callable
                       ^Callable (fn [] (f x))))
         coll)
    (mapv deref)))
```

Let's run this code and make one of the tasks cause an exception:

```clojure
(pmap (fn [x]
        (let [result (inc x)]
          (Thread/sleep 50) ;; simulate some io
          (print (str "complete " result "\n"))
          result))
  [1 2 "3" 4 5 6])

=> Error printing return value (ClassCastException)
at clojure.lang.Numbers/inc (Numbers.java:139).
class java.lang.String cannot be cast to class
java.lang.Number (java.lang.String and java.lang.Number
are in module java.base of loader 'bootstrap')

complete 7
complete 2
complete 5
complete 4
complete 6
```

Despite one of the tasks causing an exception all the other tasks keep running
and complete. This might not be the behaviour we want, particularly if we require all tasks to succeed.

This is where structured concurrency comes in.

>Simplify concurrent programming by introducing an API for structured concurrency. Structured concurrency treats groups of related tasks running in different threads as a single unit of work, thereby streamlining error handling and cancellation, improving reliability, and enhancing observability. 
>
> - JEP 462

## Structured Task Scope

First let's import the classes we will need from `StructuredTaskScope`:

```clojure
(ns server.core
  (:refer-clojure :exclude [pmap])
  (:import
   (java.util.concurrent
+    StructuredTaskScope
+    StructuredTaskScope$Subtask
+    StructuredTaskScope$ShutdownOnFailure
+    StructuredTaskScope$ShutdownOnSuccess
     ExecutorService
     Executors
     Callable)))
```

When dealing with concurrent subtasks it is common to use short-circuiting patterns to avoid doing unnecessary work. Currently, `StructuredTaskScope` provides two shutdown policies  `ShutdownOnFailure` and `ShutdownOnSuccess`. These policies shut down the scope when the first subtask fails or succeeds, respectively.

We're going to explore the `ShutdownOnFailure` shutdown policy first. 

Let's redefine our `pmap` function:

```clojure
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
```

Then run this new version with one task causing an exception:

```clojure
(pmap (fn [x]
        (let [result (inc x)]
          (Thread/sleep 50)
          (print (str "complete " result "\n"))
          result))
  [1 2 "3" 4 5 6])
  
=> Error printing return value (ClassCastException)
at clojure.lang.Numbers/inc (Numbers.java:139).
class java.lang.String cannot be cast to class
java.lang.Number (java.lang.String and java.lang.Number
are in module java.base of loader 'bootstrap')
```

As you can see the other threads are shutdown before they run/complete. Note: this depends on execution order and task completion time. Some threads might complete before the exception occurs.

Next lets look at the `ShutdownOnSuccess` shutdown policy. This policy works well in a situation where you only care about one of the results. For example reaching out to three data providers that provide the same data (for redundancy). 

We are going to implement a function called `alts` that will take the first completed task from a sequence of tasks being executed in parallel. Only failing if all tasks fail.

```clojure
(defn alts [f coll]
  (with-open [scope (StructuredTaskScope$ShutdownOnSuccess/new)]
    (run! (fn [x]
            (StructuredTaskScope/.fork scope (fn [] (f x))))
      coll)
    ;; Throws if none of the subtasks completed successfully
    (.. scope join result)))
```

Let's run `alts` and make one of the tasks cause an exception:

```clojure
(alts (fn [x]
        (let [result (inc x)]
          (Thread/sleep 100)
          (print (str "complete " result "\n"))
          result))
  [1 2 "3" 4 5 6])
  
=> 
complete 2
complete 4

2
```

We can see two of the tasks manage to complete, the rest are shutdown and only one result is returned.

Structured concurrency is a really nice addition to Java. It's great for automatically handling thread cancellation which can help keep latency down and avoid thread leaks in the case of error. 

That being said it's not a natural fit for all use cases. Sometimes you do want unstructured concurrency, like in my previous post on [Clojure: managing throughput with virtual threads](https://andersmurphy.com/2024/05/06/clojure-managing-throughput-with-virtual-threads.html) where `upmap` produces tasks in one thread and consumes their results in another.

Something I haven't covered but plan on covering in a future post is that `StructuredTaskScope` can be extended to implement your own shutdown policies.

## Dynamic var binding conveyance

Before we get on to scoped values lets explore Clojure's existing mechanism for thread bound state: dynamic vars. Dynamic vars implement a nice feature called binding conveyance which means thread context gets passed to futures and agents spawned by the parent thread. However, because `StructuredTaskScope` returns `StructuredTaskScope$Subtask`/`Supplier` and not a `future` we don't get binding conveyance automatically: 

```clojure
(def ^:dynamic *inc-amount* nil)

(binding [*inc-amount* 3]
  (pmap (fn [x]
          (let [result (+ x *inc-amount*)]
            (Thread/sleep 50)
            (print (str "complete " result "\n"))
            result))
    [1 2 3 4 5 6]))
    
=> Execution error (NullPointerException) 
at server.core/eval3782$fn (REPL:6).
Cannot invoke "Object.getClass()" because "x" is null
```

The task threads do not inherit the value of the `*inc-aomunt*` binding so we get an error. Thankfully, this is easy to fix with the `bound-fn*` function. A higher order function that transfers the current bindings to the new thread:

```clojure
(binding [*inc-amount* 3]
  (pmap
+   (bound-fn*
      (fn [x]
        (let [result (+ x *inc-amount*)]
          (Thread/sleep 50)
          (print (str "complete " result "\n"))
          result)))
    [1 2 3 4 5 6]))
    
=> complete 9
complete 6
complete 7
complete 5
complete 4
complete 8

[4 5 6 7 8 9]
```

Binding conveyance now works as we would expect.

## Scoped Values

This brings us to scoped values. These are similar to Clojure's dynamic vars and Java's thread-local variables but designed for use with virtual threads.

>Scoped values, values that may be safely and efficiently shared to methods without using method parameters. They are preferred to thread-local variables, especially when using large numbers of virtual threads. This is a preview API. 
>
> - JEP 446

With the following stated goals:

>**Goals**
>
> - **Ease of use** — Provide a programming model to share data both within a thread >and with child threads, so as to simplify reasoning about data flow.
>
> - **Comprehensibility** — Make the lifetime of shared data visible from the syntactic >structure of code.
>
> - **Robustness** — Ensure that data shared by a caller can be retrieved only by legitimate callees.
>
> - **Performance** — Allow shared data to be immutable so as to allow sharing by a large number of threads, and to enable runtime optimizations.

First let's import the classes we will need from `ScopedValue`:

```clojure
(ns server.core
  (:refer-clojure :exclude [pmap])
+ (java.lang ScopedValue)
  (:import
   (java.util.concurrent
     StructuredTaskScope
     StructuredTaskScope$Subtask
     StructuredTaskScope$ShutdownOnFailure
     StructuredTaskScope$ShutdownOnSuccess
     ExecutorService
     Executors
     Callable)))
```

Scoped values have conveyance built in as this is the behaviour that makes the most sense with hierarchical tasks:

>Subtasks forked in a scope inherit ScopedValue bindings (JEP 446). If a scope's owner reads a value from a bound ScopedValue then each subtask will read the same value.
>
> - JEP 462

Let's see how to use a single scoped value:

```clojure
(def scoped-inc-amount (ScopedValue/newInstance))

(ScopedValue/getWhere scoped-inc-amount 3
  (delay 
    (pmap (fn [x]
            (let [result (+ x (ScopedValue/.get scoped-inc-amount))]
              (Thread/sleep 50)
              (print (str "complete " result "\n"))
              result))
      [1 2 3 4 5 6])))

=> complete 4
complete 6
complete 9
complete 8
complete 5
complete 7

[4 5 6 7 8 9]
```

It's worth pointing out the use of `delay` to satisfy the `Supplier` interface. This is a recent and welcome addition in Clojure 1.12 (see [CLJ-2792](https://clojure.atlassian.net/browse/CLJ-2792)). Effectively it avoids us having to `reify` `Supplier`:

```clojure
(ScopedValue/getWhere scoped-inc-amount 3
  (reify Supplier
    (get [_]
      (pmap (fn [x]
              (let [result (+ x (ScopedValue/.get scoped-inc-amount))]
                (Thread/sleep 50)
                (print (str "complete " result "\n"))
                result))
        [1 2 3 4 5 6]))))
```

Now let's see how we set multiple scoped values: 

```clojure
(def scoped-dec-amount (ScopedValue/newInstance))

(.. (ScopedValue/where scoped-inc-amount 3)
  (ScopedValue/where scoped-dec-amount -2)
  (ScopedValue/get
    (delay
      (pmap (fn [x]
              (let [result (+ x
                             (ScopedValue/.get scoped-inc-amount)
                             (ScopedValue/.get scoped-dec-amount))]
                (Thread/sleep 50)
                (print (str "complete " result "\n"))
                result))
        [1 2 3 4 5 6]))))
        
=> complete 4
complete 2
complete 3
complete 5
complete 7
complete 6

[2 3 4 5 6 7]
```

Finally, let's make this more ergonomic by writing a convenience macro for scoped values  called `scoped-binding` that mirrors Clojure's `binding` macro:

```clojure
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

And see if it works:

```clojure
(scoped-binding [scoped-inc-amount  3
                 scoped-dec-amount -2]
  (pmap (fn [x]
          (let [result (+ x
                         (ScopedValue/.get scoped-inc-amount)
                         (ScopedValue/.get scoped-dec-amount))]
            (Thread/sleep 50)
            (print (str "complete " result "\n"))
            result))
    [1 2 3 4 5 6]))

=> complete 4
complete 6
complete 9
complete 8
complete 5
complete 7

[4 5 6 7 8 9]
```

Great, we now have all the tools for using scoped values in Clojure.

Yet again we've seen how Clojure's seamless and constantly improving integration with Java makes exploring the latest Java features effortless, and thanks to macros we can even improve on the Java experience.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/virtual-threads/structured-concurrency).

**Further Reading:**

- [JEP 462: Structured Concurrency](https://openjdk.org/jeps/462)
- [JEP 446: Scoped Values](https://openjdk.org/jeps/446)
- [Binding conveyance](https://clojure.org/reference/vars#conveyance)
- [bound-fn*](https://clojuredocs.org/clojure.core/bound-fn*)
- [Java Supplier interop CLJ-2792](https://clojure.atlassian.net/browse/CLJ-2792)
- [On the Perils of Dynamic Scope](https://stuartsierra.com/2013/03/29/perils-of-dynamic-scope)
