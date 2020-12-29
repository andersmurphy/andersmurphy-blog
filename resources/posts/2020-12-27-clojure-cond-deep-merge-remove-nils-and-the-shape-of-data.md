Title: Clojure: cond->, deep-merge, remove-nils and the shape of data

This article will cover various ways of conditionally hydrating/decorating an existing map with additional data. We'll explore different approaches and how they affect readability of the code as well as performance.

The inspiration for this article came from this [fantastic talk](https://www.youtube.com/watch?v=9fhnJpCgtUw) and its concept of being able to visualise the shape of your data.

Let's start with some data that needs to be hydrated:

```Clojure
(def heavy-ship-data
  {:ship-class "Heavy"
   :name  "Thunder"
   :main-systems {:engine {:type "Ion"}}})

(def light-ship-data
  {:ship-class "Light"
   :name  "Lightning"
   :main-systems {:engine {:type "Flux"}}})
```

The `cond->` macro is really useful for conditionally hydrating data:

```Clojure
(defn ready-ship-cond->
  [{class :ship-class :as ship-data
    {{engine-type :type} :engine} :main-systems}]
  (cond-> ship-data
    (= class "Heavy")      (assoc-in [:main-systems :shield :type]
                                     "Heavy shield")
    (= engine-type "Flux") (assoc-in [:main-systems :engine :fuel]
                                     "Fusion cells")
    (= engine-type "Flux") (assoc-in [:name] "Fluxmaster")
    true                   (assoc-in [:main-systems :engine :upgrade]
                                     "Neutron spoils")
    true                   (assoc-in [:main-systems :turret]
                                     {:type "Auto plasma incinerator"})))
```

There are a few subjective downsides to this. First it's not obvious what the shape of the data is, secondly there's repetition of paths for items that share part of their path.

But it works well enough. We conditionally `assoc-in` values into a map.

```Clojure
(ready-ship-cond-> heavy-ship-data)

=>
{:ship-class "Heavy",
 :name "Thunder",
 :main-systems
 {:engine {:type "Ion", :upgrade "Neutron spoils"},
  :shield {:type "Heavy shield"},
  :turret {:type "Auto plasma incinerator"}}}

(ready-ship-cond-> light-ship-data)

=>
{:ship-class "Light",
 :name "Fluxmaster",
 :main-systems
 {:engine
  {:type "Flux", :fuel "Fusion cells", :upgrade "Neutron spoils"},
  :turret {:type "Auto plasma incinerator"}}}
```

But what if we wanted to make this code look more like the shape of the data it actually represents. Let's imagine a function `foo-merge` that would be called like this:

```Clojure
(foo-merge
   ship-data
   {:main-systems {:turret  {:type "Auto plasma incinerator"}
                   :engine  {:upgrade "Neutron spoils"
                             :fuel    (when (= engine-type "Flux")
                                       "Fusion cells")}
                   :shield  {:type (when (= class "Heavy")
                                    "Heavy shield")}}
    :name (when (= engine-type "Flux") "Fluxmaster")})
```

I personally find this more readable. We've removed the repeating paths and the input now matches the shape of the data.

To implement `foo-merge` we need to implement a function that can merge nested maps `deep-merge`:


```Clojure
(defn deep-merge
  [& maps]
  (if (every? map? maps) (apply merge-with deep-merge maps) (last maps)))
```

We also need to implement a function that removes nil values. As the behaviour of `cond->` means it will not associate nil values:

```Clojure
(defn remove-nils
  [m]
  (clojure.walk/postwalk
   (fn [x]
     (if (map? x)
       (->> (keep (fn [[k v]] (when (nil? v) k)) x)
            (apply dissoc x))
       x))
   m))
```

Finally we can implement `deep-merge-no-nils` which should have the behaviour we desire:

```Clojure
(defn deep-merge-no-nils
  [& maps]
  (apply deep-merge (remove-nils maps)))
```

Our new implementation of the ready-ship hydrator:

```Clojure
(defn ready-ship-deep-merge-no-nils
  [{class :ship-class :as ship-data
    {{engine-type :type} :engine} :main-systems}]
  (deep-merge-no-nils
   ship-data
   {:main-systems {:turret  {:type "Auto plasma incinerator"}
                   :engine {:upgrade "Neutron spoils"
                            :fuel    (when (= engine-type "Flux")
                                       "Fusion cells")}
                   :shield {:type (when (= class "Heavy")
                                    "Heavy shield")}}
    :name (when (= engine-type "Flux") "Fluxmaster")}))
```

This doesn't quite work as we expect as it leads to insertion of empty maps in some cases `:shield {}`:

```Clojure
(= (ready-ship-cond->             heavy-ship-data)
   (ready-ship-deep-merge-no-nils heavy-ship-data))

=> true

(= (ready-ship-cond->             light-ship-data)
   (ready-ship-deep-merge-no-nils light-ship-data))

=> false

(clojure.data/diff
 (ready-ship-cond->             light-ship-data)
 (ready-ship-deep-merge-no-nils light-ship-data))

=>
(nil
 {:main-systems {:shield {}}}
 {:main-systems
  {:turret {:type "Auto plasma incinerator"},
   :engine
   {:type "Flux", :fuel "Fusion cells", :upgrade "Neutron spoils"}},
  :name "Fluxmaster",
  :ship-class "Light"})
```

Before we look into ways of solving this edge case let's see what the performance of `ready-ship-deep-merge-no-nils` vs the original implementation `ready-ship-cond->`.

To do this we use [criterium](https://github.com/hugoduncan/criterium) a great library for doing performance benchmarks in clojure:

```Clojure
(require '[criterium.core :as c])

(c/bench (ready-ship-cond-> heavy-ship-data))

=>
...
Execution time mean : 738.743093 ns
...

(c/bench (ready-ship-deep-merge-no-nils heavy-ship-data))

=>
...
Execution time mean : 16.707967 µs
...

```

Turns out `deep-merge` and `clojure.walk/postwalk` are not cheap and this leads to the
`ready-ship-deep-merge-no-nils` implementation being over 22 times slower than the `ready-ship-cond->` implementation.

When you have a visual representation of code that you like and an implementation that is less attractive but more performant you can use a macro to get the best of both worlds. Macros allow you to rewrite code at compile time from a representation you prefer to an implementation that works.

How would we get from our map representation to `cond->` and `assoc-in` implementation? First we will need the paths to each leaf node in our map:

```Clojure
(defn all-paths [m]
  (letfn [(all-paths [m path]
            (lazy-seq
             (when-let [[[k v] & xs] (seq m)]
               (cond (and (map? v) (not-empty v))
                     (into (all-paths v (conj path k))
                           (all-paths xs path))
                     :else
                     (cons [(conj path k) v]
                           (all-paths xs path))))))]
    (all-paths m [])))
```

This function returns a list of tuples containing the path and value for each leaf value in a nested map.

```Clojure
(all-paths {:ship-class "Heavy"
              :name  "Thunder"
              :main-systems {:engine {:type "Ion"}
                             :shield {:type "Phase"}}}

=>
([[:ship-class] "Heavy"]
   [[:name] "Thunder"]
   [[:main-systems :shield :type] "Phase"]
   [[:main-systems :engine :type] "Ion"])
```

We can then write a macro that builds a list of `let-bindings` and `conditions` that can be passed into a `let` and `cond->`:

```Clojure
(defmacro cond-merge [m1 m2]
  (assert (map? m2))
  (let [path-value-pairs (all-paths m2)
        symbol-pairs     (map (fn [pair] [(gensym) pair]) path-value-pairs)
        let-bindings     (mapcat (fn [[s [_ v]]] [s v]) symbol-pairs)
        conditions       (mapcat (fn [[s [path _]]]
                                   [`(not (nil? ~s)) `(assoc-in ~path ~s)])
                                 symbol-pairs)]
    `(let [~@let-bindings]
       (cond-> ~m1
         ~@conditions))))
```

It's easier to understand what's going on with this macro by using `macroexpand-1`:

```Clojure
(macroexpand-1 '(cond-merge {:a 1} {:b (when true 3) :c false }))

(clojure.core/let
    [G__26452 (when true 3) G__26453 false]
  (clojure.core/cond->
      {:a 1}
    (clojure.core/not (clojure.core/nil? G__26452))
    (clojure.core/assoc-in [:b] G__26452)
    (clojure.core/not (clojure.core/nil? G__26453))
    (clojure.core/assoc-in [:c] G__26453)))
```

Effectively, we only assoc values to `m1` if the value is not nil, where value can be the result of an expression:

```Clojure
(defn ready-ship-cond-merge
  [{class :ship-class :as ship-data
    {{engine-type :type} :engine} :main-systems}]
  (cond-merge
   ship-data
   {:main-systems {:turret  {:type "Auto plasma incinerator"}
                   :engine  {:upgrade "Neutron spoils"
                             :fuel    (when (= engine-type "Flux")
                                        "Fusion cells")}
                   :shield  {:type (when (= class "Heavy")
                                     "Heavy shield")}}
    :name (when (= engine-type "Flux") "Fluxmaster")}))
```

Not only does the `ready-ship-cond-merge` implementation produce the exact same result as `ready-ship-cond->`:

```Clojure
(= (ready-ship-cond->             heavy-ship-data)
   (ready-ship-cond-merge    heavy-ship-data))

=> true

(= (ready-ship-cond->             light-ship-data)
   (ready-ship-cond-merge    light-ship-data))

=> true
```

It's also just as performant!

```Clojure
(c/bench (ready-ship-cond-merge    heavy-ship-data))

=>
...
Execution time mean : 775.762294 ns
...

```

Though it is worth pointing out that `cond-merge` macro does have some limitations/unexpected behaviour when it comes to nested condition and conditions that return maps. This can lead to overwriting data rather than merging. In the example below `:b` no longer contains `:e 3`. This is what `assoc-in` would do but not what a `deep-merge` would do.

```Clojure
(cond-merge {:a 1
             :b {:e 3}}
{:b (when true {:c 1 :d 2})
 :c false})

=>
{:a 1
 :b {:c 1 :d 2}
 :c false}
```

If you separate out the conditions for each value then you do get the expected result.

```Clojure
(cond-merge {:a 1
             :b {:e 3}}
            {:b {:c (when true 1)
                 :d (when true 2)}
             :c false})

=>
{:a 1
 :b {:e 3
     :c 1
     :d 2}
 :c false}
```

In this post we've seen how to use code as data and macros to develop a more readable  data literal representation that captures the shape of our output data. Improving programmer ergonomics without sacrificing performance. We've also learnt that getting the semantics of macros right isn't always easy.
