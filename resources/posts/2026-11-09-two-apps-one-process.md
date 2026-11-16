title:  Two apps one process

*So what if you can handle thousands of concurrent users per core? Even the front page of HN gets you what? 100 concurrent users on a good day?*

More like 30.

*Exactly! Most successful apps have like what 10 or 20 concurrent users at any given time? This is basically academic for most projects. What does performance give you other than knowing that your app won't need to scale past a single server? Pointless!*

Well what if you could run multiple apps in one process?

*Isn't that the same as a bunch of apps behind a reverse proxy?*

No. It's not. Separate processes have to compete for OS resources, have their own thread pools, schedulers, heap, GC, caches and so on. Each process needs to be sized relative to the other apps running on the system.

But, in a single process you can size the process for the machine and then run an arbitrary number of apps in that process. If the apps share a process, they can share all sorts of things: a single JVM, GC, caches, buffers, http server, HTTPS certificate management, batching, game loop etc. You'd eliminate thread contention between apps. This lets you scale further with less resources!

Performance lets you run as many apps as you want in that one process.

*...*

> Disclaimer: all my writing and code is handcrafted by me. By the end of this you may wish it wasn't.

## Some numbers

If you have multiple JVMs you duplicate a bunch of fixed costs:

- Separate heaps
- Separate JIT
- Separate GC

If you have multiple HTTP servers you duplicate:

- event loop threads
- byte buffers
- queues

If you have multiple framework engines you duplicate:

- html buffers
- render threads
- fragment caches
- queues

All of this adds up to more memory, more threads and more contention. If you own all your app and don't need the isolation a process gives you, then it can be a big performance win to have one JVM, one http server, and one framework engine.

*Where were the numbers?*

`X * n` where `X` is your fixed cost for the above and `n` is your number of apps. If all of the above takes 500MB, then four apps is an extra 1.5GB.

## What is an app?

*A miserable little pile of secrets*

Indeed. If you think about it an app at its simplest is a domain, a database and some routes.

```clojure
(def app
  {:domain   "example.andersmurphy.com"
   :db       {:name          "gol.db"
              :migrations    migrations
              :pragma-writer {:cache_size 15625}
              :pragma
              {:cache_size 2000
               :page_size  4096
               ;; 1GB
               :mmap_size  1000000000}}
   :routes   #'routes
   :batch-fn #'batch-fn})
```

## HTTPS certificate management with Clave

*But, my reverse proxy handles HTTPS certs and stuff! Where is your god now?*

Yes certs are a pain. But [Clave: Automatic HTTPS certificate management and renewal via ACME, implemented in pure Clojure with minimal dependencies](https://github.com/outskirtslabs/clave) exists. I use it with aleph. But, it also works with jetty and it's simple to extend to other servers.

```clojure
(clave-aleph/start-server router
  (merge
    config
    {:http-versions             [:http2 :http1]
     ::clave-aleph/http-options {:port 80}
     ::clave-aleph/config
     {:domains (mapv :domain apps)
      :issuers
      [{:directory-url
        "https://acme-v02.api.letsencrypt.org/directory"
        :email email}]}}))
```

So no reverse proxy needed.

## Bundling our apps

To bundle our apps we import them as dependencies into a separate project whose only purpose is to configure and start the server.

```clojure
(ns app.main
  (:gen-class)
  (:require
   [hyperlith.core :as h]
   [billion-cells.main :as cells]
   [billion-checkboxes.main :as boxes]
   [game-of-life.main :as gol]
   [clj-async-profiler.core :as prof]))

(defn start-app! [& {:keys [mode]}]
  (h/start-server!
+   {:apps               [cells/app boxes/app]
     :tick-ms            100
     :render-buffer-size (* 64 16384)
     :email              (h/env :email)
     :mode               mode}))

(defn -main [& _]
  (start-app! :mode :prod))
```

So adding a new app is as simple as importing it and adding it to the list of apps:

```clojure
:apps [cells/app boxes/app gol/app]
```

We can then build this project as an uberjar and `scp` it to the server.

## Deployment

*Well this is nice and all, but whenever you deploy an app, you restart the process. DOWNTIME! UNACCEPTABLE!*

Here's a gif of me deploying the jar to production:

![deploy gif](/assets/deploygol.gif)

If a deploy is indistinguishable from network blip did the app really go down?

## Conclusion

That's it. We've reduced the overhead and cost of deploying a new app to zero.

Code [can be found here](https://github.com/andersmurphy/hyperlith). All three apps are running on the same JVM, on the same 10$ VPS , [here](https://example.andersmurphy.com/), [here](https://checkboxes.andersmurphy.com/) and [here](https://cells.andersmurphy.com/).

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.

### Discussion
- [reddit](https://www.reddit.com/r/Clojure/comments/1xs5dv3/two_apps_one_process/)
- [lobsters](https://lobste.rs/s/sifkms/two_apps_one_process)
