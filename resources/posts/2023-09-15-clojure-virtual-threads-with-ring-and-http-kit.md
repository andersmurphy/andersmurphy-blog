Title: Clojure: virtual threads with ring and http-kit

[Java 19 introduce virtual threads](https://openjdk.org/jeps/425) as a preview feature. Virtual threads are lightweight threads that allow you to write server applications in a simple thread-per-request style (as opposed to async style) and still scale with near-optimal hardware utilisation. The thread-per-request approach is generally easier to reason about, maintain and debug than it's asynchronous counterparts. In this post we'll cover configuring [http-kit](https://github.com/http-kit/http-kit) to make your ring handlers use virtual threads and benchmark it against regular threads.

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
  (:require [org.httpkit.server :as hk-server]
            ...)
  (:import (java.util.concurrent Executors)))

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

First we will create a http-kit server without virtual threads so that we can make a performance comparison.

```Clojure 
(def server (hk-server/run-server #'app
                {:port 8080}))
```

Then benchmark it with [wrk (a http server benchmarking tool)](https://github.com/wg/wrk).

```
wrk -t 12 -c 120 http://127.0.0.1:8080/hello

Running 10s test @ http://127.0.0.1:8080/hello
12 threads and 120 connections
Requests/sec:     76.00
Transfer/sec:     10.98KB
```

Stop the http-kit server.

```Clojure 
(server)
```

Now, we will create a http-kit server that uses virtual threads.

```Clojure
(def server (hk-server/run-server #'app
              {:port        8080
               :worker-pool (Executors/newVirtualThreadPerTaskExecutor)}))
```

Then benchmark it.

```
wrk -t 12 -c 120 http://127.0.0.1:8080/hello

Running 10s test @ http://127.0.0.1:8080/hello
12 threads and 120 connections
Requests/sec:   2208.67
Transfer/sec:    319.22KB
```

That's a 29x increase in performance by switching to virtual threads. Keep in mind, this is a very crude benchmark, and you should always do you're own project specific benchmarking. That being said the above example gives an indication of the potential benefits to switching to virtual threads, especially in the case where requests are doing a fair bit of work (e.g: querying a database).

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/virtual-threads/http-kit).

For setting up [virtual threads with jetty checkout this post](https://andersmurphy.com/2023/09/16/clojure-virtual-threads-with-ring-and-jetty.html).
