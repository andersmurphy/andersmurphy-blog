Title: Clojure: flattening key paths

This article will cover how to flatten the nested key paths of a map in Clojure; turning a nested map like `{:a {:b {:c {:d 1} :e 2}}}` into a flat map like `{:a-b-c-d 1 :a-b-e 2}`.

## Flattening key paths recursively

We `map` over the key value pairs of the top level map. If we encounter a `map?` that is `not-empty`, we `conj` the current key onto a vector, thus gradually building a path and then call `flatten-paths` recursively. If we encounter anything else, we take the current path and turn it into a single key:

```clojure
(defn flatten-paths
  ([m separator]
   (flatten-paths m separator []))
  ([m separator path]
   (->> (map (fn [[k v]]
               (if (and (map? v) (not-empty v))
                 (flatten-paths v separator (conj path k))
                 [(->> (conj path k)
                       (map name)
                       (clojure.string/join separator)
                       keyword) v]))
             m)
        (into {}))))
```

This works for shallow maps:

```clojure
(flatten-paths {:a {:b {:c {:d 1}
                        :e 2}}
                :f 3}
                "-")

=> {:a-b-c-d 1, :a-b-e 2, :f 3}
```

But does it work with deeply nested maps? To check this we first need to write a function `create-n-nested-map` that will create a deeply nested map:

```clojure
(defn create-n-nested-map [n]
  (assoc-in {} (repeat n :a) {}))
```

And a harness `find-when-overflow-occurs` to tell us roughly at what depth we get a `StackOverflowError`:

```clojure
(defn find-when-overflow-occurs [f n]
  (if (try
        (f n)
        (catch StackOverflowError e
          false))
    (recur f (inc n))
    n))

(find-when-overflow-occurs
          (flatten-paths (create-n-nested-map n) "-")
          0)

=> 599
```

On my machine I get a stack overflow at a depth of 600. In practice, you are unlikely to encounter a map that is this deeply nested. However, lets see if we can refactor `flatten-paths` to use the sequence abstraction so that it can handle deeper maps.

## Flattening key paths with lazy-seq

The `lazy-seq` takes a body of expressions that returns an `ISeq` or `nil` and yields a `Seqable` object that will invoke the body only the first time `seq`
is called. It will cache the result and return it on all subsequent
seq calls. The caching of the body is what allows the `lazy-seq` macro to call itself recursively without consuming more stackframes the way a normal recursive call would.

We use the `lazy-seq` to define a function that recursively builds a list of the flattened key paths. The use of `(when-let [[x & xs] (seq s)] ...)` is a common pattern when building lazy sequences, allowing you to apply a function to the head of the sequence and then call it recursively on the tail. It's also worth noting that to combine the output of the head and the tail operations we use `into` (strict) rather than `concat` (lazy). For more details check out [this article](https://stuartsierra.com/2015/04/26/clojure-donts-concat).

```clojure
(defn flatten-paths [m separator]
  (letfn [(flatten-paths [m separator path]
            (lazy-seq
             (when-let [[[k v] & xs] (seq m)]
               (cond (and (map? v) (not-empty v))
                     (into (flatten-paths v separator (conj path k))
                           (flatten-paths xs separator path))
                     :else
                     (into [(->> (conj path k)
                                 (map name)
                                 (clojure.string/join separator)
                                 keyword) v]
                           (flatten-paths xs separator path))))))]
    (->> (flatten-paths m separator [])
         (apply hash-map))))

(flatten-paths (create-n-nested-map 1000000) "-")

=>
Execution error (StackOverflowError) at user/flatten-paths...

```

But for some reason we are still getting a `StackOverflowError`. Maybe it's our `create-n-nested-map` function that's the culprit?

```clojure
(find-when-overflow-occurs
 create-n-nested-map
 0)

=> 5889
```

Interesting... so `assoc-in` seems to be the cause. I guess this highlights just how unlikely you are to encounter such ridiculously nested maps. Let's rewrite `create-n-nested-map` to build the map from the inside out with `reduce` rather than outside in with `assoc-in`.

```clojure
(defn create-n-nested-map [n]
  (reduce (fn [acc _] {:a acc}) {} (range n)))
```

Let's see if that works. Warning! The output is quite large and will flood your repl.

```clojure
(flatten-paths (create-n-nested-map 1000000) "-")

=> {:a-a-a-a-a-a... {}}
```

Rejoice! We can now flatten the key paths of ridiculously deeply nested maps.

This concludes this guide to flattening key paths in Clojure.
