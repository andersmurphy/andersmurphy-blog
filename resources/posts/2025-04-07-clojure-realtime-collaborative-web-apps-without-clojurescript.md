title: Clojure: Realtime collaborative web apps without ClojureScript

Last week I made a fun little multiplayer web app. [Go check it out here](https://example.andersmurphy.com).

... so you're back ...

That app had zero ClojureScript! What's even more wild is it had zero user written client side JS. Instead it uses [Datastar](https://data-star.dev). A tiny 11.4kb (brotli compressed) JS framework that lets you write reactive web apps with simple server side rendering.

This post is intended as a very quick introduction to some of the concepts and features of Datastar. So buckle up.

*Note:  I've rolled my own opinionated Clojure mini framework that builds on Datastar called [hyperlith](https://github.com/andersmurphy/hyperlith), but Datastar itself is both backend language and framework agnostic.*

## The same model as React

With Datastar you can still use the same `view = f (state)` model. The difference is  the `view` is on the client and `f (state)` stays on the server.

## Show me the code

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

That's the neat part you don't. It already is. The function we defined in `render-home` does not distinguish between users so everyone sees the same thing! If we wanted to render different views for different users, we would just generate user specific views in that function.

The function `action-tap-cell` does distinguish between users though. It picks a colour based on their `sid`.

If you've played around with [Electric Clojure](https://github.com/hyperfiddle/electric) you might find this familiar.

## This code is declarative (and naive)

![event listener image](/assets/naive.png)

There's no canvas here. There's no SVG. There's just a 1600 cell grid, each cell with it's own on-click listener. This is an incredibly naive implementation. Partly, to show how well it performs. Your CRUD app will be fine.

## But what about the network?

Brotli compression over SSE gives you 50-100:1 compression ratio over a series of backend re-renders. The compression is so good that in my experience it's more network efficient and more performant that fine grained updates with diffing (without any of the additional complexity). This approach avoids the additional challenges of view and session maintenance.

## Conclusion

[Datastar](https://data-star.dev) pairs really well with Clojure and can make it trivial to implement highly interactive and collaborative web apps without ClojureScript. You should give it a go!

The full Datastar game of life source code can [be found here](https://github.com/andersmurphy/hyperlith/blob/master/examples/game_of_life/src/app/main.clj).

**Further Reading:**

- [Datastar docs](https://data-star.dev/guide/getting_started)
- [More hyperlith examples](https://github.com/andersmurphy/hyperlith/tree/master/examples)
- [More of my thoughts on using Datastar](https://github.com/andersmurphy/hyperlith?tab=readme-ov-file#rational-more-like-a-collection-of-opinions)
