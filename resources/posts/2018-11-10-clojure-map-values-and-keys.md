Some useful clojure functions for transforming the keys and values of maps.

<!--more-->

### map-values

When working in clojure I often find I want to transform the values of a map and apply the same function to each value. This is easy to do in clojure as the map function will break a map data structure into key value tuples that are easy to manipulate. That being said, I find it comes up often enough that having a more specialised function is not only more convenient but conveys the code's intention more clearly.

```clojure
(defn map-values [f m]
  (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

user=> (map-values #(inc %) {:a 1 :b 2 :c 3})
{:a 2 :b 3 :c 4}
```

This function is specifically for map collection types but there is a more generic version called [fmap](https://github.com/clojure/algo.generic/blob/master/src/main/clojure/clojure/algo/generic/functor.clj#L19) available in the `clojure/algo.generic` library.

### map-keys

Another common task I run into is transforming the keys of a map. This is especially useful when you are at the edge of your codebase and wanting to communicate to another system that might have different conventions; for example when sending up analytic events. The analytic system might camel case its keys or map keys to completely different words or domain language.

```clojure
(defn map-keys [f m]
  (->> (map (fn [[k v]] [(f k) v]) m)
       (into {})))

user=> (map-keys #(str "beta-" (name %))
         {:sign-up "event" :log-out "event"})
{:sign-up "beta-event" :log-out "beta-event"}

(def keys->analytics-event-names
  {:message-sent         "Primary announcement made to group"
   :transaction-complete "Item purchased"})

user=> (map-keys keys->analytics-event-names
         {:message-sent         "event"
          :transaction-complete "event"})
{"Primary anouncement made to group" "event",
 "Item purchased"                    "event"}
```

I hope these functions come in handy.
