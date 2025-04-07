title: Clojure: Realtime collaborative web apps without ClojureScript

Last week I made a fun little multiplayer web app. I've embedded it below:

<iframe src="https://example.andersmurphy.com" title="Game of Life"
style="width: 100%;	aspect-ratio: 2/3;	max-width: 400px"></iframe>

A few things to note about this web app:

- It is streaming the whole `<main>` element of the page from the server to the client every 200ms over SSE (server sent events).
- It has zero ClojureScript.
- It has zero user written JS.
- It uses a tiny 11.4kb (brotli compressed) hypermedia framework called [Datastar](https://data-star.dev).

## What about performance?

Surely sending down the whole main body on every change is terrible for performance?!

![event listener image](/assets/naive.png)

There's no canvas here. There's no SVG. There's just a 1600 cell grid, each cell with it's own on-click listener. This is an incredibly naive implementation. Partly, to show how well it performs. Your CRUD app will be fine.

Under the hood Datastar uses a very fast morph algorithm that merges the old `<main>` fragment with the new `<main>` fragment only updating what has changed. 

## What about the network?

Surely sending down the whole main body on every change is terrible for bandwidth?! 

Turns out streaming compression is really good. Brotli compression over SSE (server sent events) gives can give you a 70-100:1 compression ratio over a series of backend re-renders. The compression is so good that in my experience it's more network efficient and more performant that fine grained updates with diffing (without any of the additional complexity). This approach also avoids the additional challenges of view and session maintenance.

## Isn't this just another Phoenix Live View clone?

No, it's much simpler then that. There's no connection state, server side diffing or web sockets. There's no reason the client has to connect/communicate with the same node. Effectively making it stateless.

## Do I have to learn a new UI model?

With Datastar you can still use the same `view = f (state)` model that react uses. The difference is  the `view` is on the client and `f (state)` stays on the server.

## Show me the code!

In this example I'll be using [hyperlith](https://github.com/andersmurphy/hyperlith) an experimental mini framework that builds on Datastar. It handles a few things for us that you'd normally have to manage yourself with Datastar (SSE, compression, connections, re-render rate, missed events, etc).

*Note: Datastar itself is both backend language and framework agnostic.*

Lets start with a minimal shim, this is for the initial page load:

```clojure
(def default-shim-handler
  (h/shim-handler
    (h/html
      [:link#css {:rel "stylesheet" :type "text/css" :href (css :path)}]
      [:title nil "Game of Life"]
      [:meta {:content "Conway's Game of Life" :name "description"}])))
```

Then we have a hiccup render function with a separate component `board-state` component:

```clojure
(def board-state
  (h/cache
    (fn [db]
      (map-indexed
        (fn [id color-class]
          (h/html
            [:div.tile
             {:class         color-class
              :id            id
              :data-on-click (format "@post('/tap?id=%s')" id)}]))
        (:board @db)))))

(defn render-home [{:keys [db] :as _req}]
  (h/html
    [:link#css {:rel "stylesheet" :type "text/css" :href (css :path)}]
    [:main#morph.main
     [:h1 "Game of Life (multiplayer)"]
     [:div
      [:div.board nil
       (board-state db)]]]))
```

A user action:

```clojure
(defn action-tap-cell [{:keys [sid db] {:strs [id]} :query-params}]
  (swap! db fill-cross (parse-long id) sid))
```

Some routes:

```clojure
(def router
  (h/router
    {[:get (css :path)] (css :handler)
     [:get  "/"]        default-shim-handler
     [:post "/"]        (h/render-handler #'render-home)
     [:post "/tap"]     (h/action-handler #'action-tap-cell)}))
```

We pass the `render-home` and `action-tap-cell` functions into some helper functions that build handlers and we are good to go.

## So how do you change this code to make it multiplayer?

That's the neat part, you don't. It already is multiplayer. The function we defined in `render-home` does not distinguish between users so everyone sees the same thing! If we wanted to render different views for different users, we would just generate user specific views in that function.

The function `action-tap-cell` does distinguish between users though. It picks a colour based on their `sid`.

If you've played around with [Electric Clojure](https://github.com/hyperfiddle/electric) you might find this familiar.

## Conclusion

[Datastar](https://data-star.dev) pairs really well with Clojure and can make it trivial to implement highly interactive and collaborative web apps without ClojureScript. You should give it a go!

The full Datastar game of life source code can [be found here](https://github.com/andersmurphy/hyperlith/blob/master/examples/game_of_life/src/app/main.clj).

**Further Reading:**

- [Datastar docs](https://data-star.dev/guide/getting_started)
- [More hyperlith examples](https://github.com/andersmurphy/hyperlith/tree/master/examples)
- [More of my thoughts on using Datastar](https://github.com/andersmurphy/hyperlith?tab=readme-ov-file#rational-more-like-a-collection-of-opinions)
- [Direct link to the game](https://example.andersmurphy.com).
