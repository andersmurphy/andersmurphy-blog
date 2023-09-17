Title: Clojure: virtual threads with ring and jetty

[Java 19 introduce virtual threads](https://openjdk.org/jeps/425) as a preview feature. Virtual threads are lightweight threads that allow you to write server applications in a simple thread-per-request style (as opposed to async style) and still scale with near-optimal hardware utilisation. The thread-per-request approach is generally easier to reason about, maintain and debug than it's asynchronous counterparts. In this post we'll cover configuring [Jetty](https://github.com/ring-clojure/ring/tree/master/ring-jetty-adapter) to make your ring handlers use virtual threads and benchmark it against regular threads.

If you're running Java 19+ you can enable preview features with `--enable-preview` as shown below. This step won't be needed in Java 21 (which is being released this month) as virtual threads will be out of preview and available by default.

```Clojure
{:paths   ["src"]
 :deps    {...}
 :aliases {:dev {:jvm-opts ["--enable-preview"]}}}
```

Set up a ring handler that sleeps for 50 milliseconds to simulate a long request.

```Clojure
(ns server.core
  (:gen-class)
  (:require [ring.adapter.jetty :refer [run-jetty]]
            ...)
  (:import (java.util.concurrent Executors)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))
           
(def app
  (ring/ring-handler
    (ring/router
      ["/hello"
       {:get
        {:handler
         (fn [_]
           {:headers {"Content-Type" "text/html"}
            :status  200
            :body    (do
                       ;; simulate work by sleeping
                       ;; for 50 milliseconds
                       (Thread/sleep 50)
                       (h/render [:b "hello!"]))})}}]
      ...)))
```

First we will create a jetty server without virtual threads so that we can make a performance comparison.

```Clojure
(def jetty-server (run-jetty app {:port 8080 :join? false}))
```

Then benchmark  it with [wrk (a http server benchmarking tool)](https://github.com/wg/wrk).

```
wrk -t 12 -c 120 http://127.0.0.1:8080/hello

Running 10s test @ http://127.0.0.1:8080/hello
12 threads and 120 connections
Requests/sec:    842.92
Transfer/sec:     95.49KB
```

Stop the jetty server.

```Clojure 
(.stop server)
```

Now, we will create a jetty server that uses virtual threads.

```Clojure
(def jetty-server
  (let [thread-pool (new QueuedThreadPool)
        _           (.setVirtualThreadsExecutor thread-pool
                      (Executors/newVirtualThreadPerTaskExecutor))]
    (run-jetty app {:port        8080
                    :join?       false
                    :thread-pool thread-pool})))
```

Then benchmark it.

```
Running 10s test @ http://127.0.0.1:8080/hello
12 threads and 120 connections
Requests/sec:   2218.19
Transfer/sec:    251.28KB
```

That's a 2.6x increase in performance by switching to virtual threads. Keep in mind, this is a very crude benchmark, and you should always do you're own project specific benchmarking. That being said the above example gives an indication of the potential benefits to switching to virtual threads, especially in the case where requests are doing a fair bit of work (e.g: querying a database).

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/virtual-threads/jetty).

*Note: The virtual thread set up above also works with [ring-jetty9-adapter](https://github.com/sunng87/ring-jetty9-adapter) which supports [Jetty 12 and it's new AdaptiveExecutionStrategy](https://webtide.com/jetty-12-virtual-threads-support/).*

For setting up [virtual threads with http-kit checkout this post](https://andersmurphy.com/2023/09/15/clojure-virtual-threads-with-ring-and-http-kit.html).
