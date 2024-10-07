title:  Clojure: Synchronous server sent events with virtual threads and channels

I have been playing around with [turbo](https://turbo.hotwired.dev/) and [datastar](https://data-star.dev/) both of which use [server sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events) to broadcast updates to the client. Traditionally, server sent events work best with asynchronous ring handlers. But, personally I much prefer the simplicity of synchronous handlers, and with the advent of virtual threads we can have our cake and eat it. This post will go over how to build a synchronous ring handler for server sent events.

## Extending StreamableResponseBody

First we want to extend `StreamableResponseBody` protocol to support `ManyToManyChannel`. Even though we are using virtual threads we still want a way for these virtual threads to communicate with each other and `core.async` channels are perfect for this. In theory `BufferedWriter` is thread safe so you could skip `core.async` if you really want to, but you'd miss out on useful features like `sliding-buffer` etc.

```clojure
;; Extend core.async channel with StreamableResponseBody
(extend-type clojure.core.async.impl.channels.ManyToManyChannel
  StreamableResponseBody
  (write-body-to-stream [ch _response ^OutputStream output-stream]
    (with-open [out    output-stream
                writer (io/writer out)]
      (try
        (loop []
          (when-let [^String msg (a/<!! ch)]
            (doto writer (.write msg) (.flush))
            (recur)))
        ;; If the client disconnects writing to the output stream
        ;; throws an IOException.
        (catch java.io.IOException _)
        ;; Close channel after client disconnect.
        (finally (a/close! ch))))))
```

This code takes a message off the channel and writes it to the output-stream. If a write fails we close the channel. It's important to note this code uses `a/<!!` so is blocking.

## Tracking client connections

Next lets create an atom to keep track of client connections:

```clojure
(defonce clients (atom #{}))
```

## Sending messages

Some code for sending messages:

```clojure
(defn format-event [body]
  (str "data: " body "\n\n"))

(defn send>!! [ch message]
  (let [v (a/>!! ch message)]
    (when-not v (swap! clients disj ch))
    ;; Keeps the return semantics of >!!
    v))
```

When a channel is closed, we want to remove the clients connection from the `clients` atom.

## Heartbeat

There's no way for us to know that the client has closed the connection without trying to write to the output-stream. To prune disconnected clients, so that we don't have an ever expanding atom of zombie clients, we need to occasionally write to the output stream. To do this we send  a `\n\n` message every X seconds. This heartbeat runs in a virtual thread and stops when the channel is closed. 

*Note: this code is very similar to what you would normally write in a `go` block but instead we are using the blocking `core.async` constructs and running them in a virtual thread.*

```clojure
(defn heartbeat>!! [ch msec]
  (Thread/startVirtualThread
    #(loop []
       (Thread/sleep ^long msec)
       (when (send>!! ch "\n\n")
         (recur)))))
```

## The handler

Finally, our synchronous handler creates a channel, adds the channel to the `clients` atom, sends a connection message and starts a heartbeat. The channel is then returned as the response body.

```clojure
(defn handler-sse [_]
  (let [ch (a/chan 10)]
    (swap! clients conj ch)
    (send>!! ch (format-event "Successfully connected"))
    ;; Every 10 seconds we send a heartbeat to check if output stream
    ;; is still open.
    (heartbeat>!! ch 10000)
    {:status  200
     :headers {"Content-Type"  "text/event-stream"
               "Cache-Control" "no-cache, no-store"}
     :body    ch}))
```

The headers are important here, we don't want the client to cache responses and the content type needs to be `text/event-stream`.

## Broadcast

We need a broadcast function to broadcast messages to clients. All it does is iterates through each channel in `clients` and sends a message.

```clojure
(defn broadcast-message-to-connected-clients! [message]
  (run! (fn [ch] (send>!! ch (format-event message))) @clients))
```

## Simple server

We need a server configured to use virtual threads. Virtual threads means we can have hundreds of thousands of threads with very little overhead. This is what enables us to write the handlers in a synchronous manner without running into performance problems. 

*Note:  you'll need to be running Java 21+ to have access to Virtual threads.*

```clojure
(def app
  (fn handler [{:keys [request-method uri] :as req}]
    (if (= [:get  "/"] [request-method uri])
      (handler-sse req)
      {:status 404})))

(defn start-server [& {:as opts}]
  (let [thread-pool (new QueuedThreadPool)
        _           (.setVirtualThreadsExecutor thread-pool
                      (Executors/newVirtualThreadPerTaskExecutor))]
    (run-jetty  #'app
      (merge
        {:port        8080
         :thread-pool thread-pool}
        opts))))
```

Start the server.

```clojure
(def server
  (start-server :join? false))
```

## Connecting clients

Connect a client from a terminal. You can connect multiple clients using different terminal windows.

```bash
curl localhost:8080 -vv

=>

*   Trying [::1]:8080...
* Connected to localhost (::1) port 8080
> GET / HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/8.4.0
> Accept: */*
> 
< HTTP/1.1 200 OK
< Server: Jetty(12.0.13)
< Content-Type: text/event-stream;charset=UTF-8
< Cache-Control: no-cache, no-store
< Transfer-Encoding: chunked
< 
data: Successfully connected

```

## Broadcast  messages

Send a message to all clients.

```clojure
(broadcast-message-to-connected-clients! "Hello")
```

You should see the message print in each connected terminal.

```bash
data: Hello
```

## Check connected clients

If we deref the `clients` atom we can see that we have two connected clients.

```clojure
@clients
=>
#{#object[clojure.core.async.impl.channels.ManyToManyChannel 0x31de488f "clojure.core.async.impl.channels.ManyToManyChannel@31de488f"]
  #object[clojure.core.async.impl.channels.ManyToManyChannel 0x7870479b "clojure.core.async.impl.channels.ManyToManyChannel@7870479b"]}
```

## Pruning disconnected clients

If we close the terminal windows (disconnecting the clients) and wait 30 seconds or so and deref the `clients` atom again it should be empty.

```clojure
@clients
=>
#{}
```

## Conclusion

Hopefully, this post provides a good starting point for anyone wanting to set up server sent events with synchronous ring handlers. 

As an aside `core.async` channels and their blocking constructs work really nicely with virtual threads. In my experience so far it means you never have to reach for `go` blocks. This massively simplifies things.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/server-sent-events/synchronous-handler-with-virtual-threads).

**Further Reading:**

- [Server-sent events(SSE) with Ring and Compojure](https://www.lucagrulla.com/posts/server-sent-events-with-ring-and-compojure/)
- [Clojure bites - SSE with Aleph and Reitit](https://fpsd.codes/blog/clojure-bites-sse/)
- [Server-Sent Events: the alternative to WebSockets you should be using](https://germano.dev/sse-websockets/)
