Title: Clojure: map-values and map-keys

This post covers some useful Clojure functions for transforming the keys and values of maps.

## map-values

When writing Clojure, I often find I want to transform the values of a map and apply the same function to each value. Clojure makes this easy as the map function breaks a map data structure into key-value tuples that are easy to manipulate. I find this comes up often enough that having a more specialised function is not only more convenient but conveys the code's intention more clearly.

```clojure
(defn map-values [f m]
  (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

(map-values inc {:a 1 :b 2 :c 3})

=> {:a 2 :b 3 :c 4}

(map-values #(str "beta-" %)
 {:sign-up "event" :log-out "event"})

=> {:sign-up "beta-event" :log-out "beta-event"}
```

This function is specifically for map collection types, but there is a more generic version called [fmap](https://github.com/clojure/algo.generic/blob/master/src/main/clojure/clojure/algo/generic/functor.clj#L19) available in the `clojure/algo.generic` library.

**UPDATE:** *As of Clojure 1.11.0 there is now a built in function in `clojure.core` called `update-vals` which behaves identically to `map-values` but takes the arguments in the opposite order `(update-vals m f)`.*

## map-keys

Another common task I run into is transforming the keys of a map. Useful when you are at the edge of your codebase and want to communicate to another system that might have different conventions; for example when sending up analytic events. The analytic system might camel case its keys or map keys to entirely different words or domain language.

```clojure
(defn map-keys [f m]
  (->> (map (fn [[k v]] [(f k) v]) m)
       (into {})))

(map-keys #(str "beta-" (name %))
 {:sign-up "event" :log-out "event"})

=> {"beta-sign-up" "event" "beta-log-out" "event"}

(def keys->analytics-event-names
  {:message-sent         "Primary announcement sent"
   :transaction-complete "Item purchased"})

(map-keys keys->analytics-event-names
 {:message-sent         "event"
  :transaction-complete "event"})

=> {"Primary anouncement sent" "event",
    "Item purchased"           "event"}
```

There's also a core function called `clojure.set/rename-keys` which does a similar thing to our `map-keys` function.

```clojure
(doc clojure.set/rename-keys)

=>
-------------------------
clojure.set/rename-keys
([map kmap])
  Returns the map with the keys in kmap renamed to the vals in kmap
```

The downside with this function is it takes a key-map not a function, meaning it's only useful for mapping keys. The upside is that it leaves keys that are not in the key-map unchanged.

```clojure
(def keys->analytics-event-names
  {:message-sent         "Primary announcement sent"
   :transaction-complete "Item purchased"})

(map-keys keys->analytics-event-names
 {:message-sent         "event"
  :transaction-complete "event"
  :missing-key          "event"})

=> {"Primary anouncement sent" "event",
    "Item purchased"           "event"
    nil                        "event"}

(clojure.set/rename-keys
 {:message-sent         "event"
  :transaction-complete "event"
  :missing-key          "event"}
  keys->analytics-event-names)

=> {:missing-key                "event"
    "Primary announcement sent" "event"
    "Item purchased"            "event"}
```

**UPDATE:** *As of Clojure 1.11.0 there is now a built in function in `clojure.core` called `update-keys` which behaves identically to `map-keys` but takes the arguments in the opposite order `(update-keys m f)`.*

I hope these functions come in handy.
