Title: Clojure: flattening key paths

This article will cover how flatten the nested key paths of a map in Clojure.

### Flattening key paths recursively

So we want to turn a nested structure.

```clojure
{:a {:b {:c {:d 1}
         :e 2}}}
```

Into a flat structure.

```clojure
{:a-b-c-d 1 :a-b-e 2}
```

We can do this recursively. We `map` over the key values pairs of the top level map. If we encounter a `map?` that is `not-empty` we `conj` the current key onto a vector, thus gradually building a path and then call `flatten-paths` recursively. If we encounter anything else we take the current path and turn it into a single key.


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

This works with shallow maps

```clojure
(flatten-paths {:a {:b {:c {:d 1}
                        :e 2}}
                :f 3}
                "-")

=> {:a-b-c-d 1, :a-b-e 2, :f 3}
```

But does it work with deeply nested maps. Let create a function that will create

```clojure
(defn create-n-nested-map [n]
  (assoc-in {} (repeat n :a) {}))
```

```clojure
(flatten-paths (create-n-nested-map 5000) "-")

=>
Execution error (StackOverflowError) at user/flatten-paths...
```

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

```clojure
(defn flatten-paths [m separator]
  (letfn [(flatten-paths [m separator path]
            (lazy-seq
             (when-let [[[k v] & xs] (seq m)]
               (cond (and (map? v) (not-empty v))
                     (cons (flatten-paths v separator (conj path k))
                           (flatten-paths xs separator path))
                     :else
                     (cons [(->> (conj path k)
                                 (map name)
                                 (clojure.string/join separator)
                                 keyword) v]
                           (flatten-paths xs separator path))))))]
    (->> (flatten-paths m separator [])
         flatten
         (apply hash-map))))

(flatten-paths {:a {:b {:c {:d 1}
                        :e 2}}
                :f 3}
                "-")

=> {:a-b-c-d 1, :a-b-e 2, :f 3}

(flatten-paths (create-n-nested-map 1000000) "-")

=>
Execution error (StackOverflowError) at user/flatten-paths...

```

```clojure
(find-when-overflow-occurs
 create-n-nested-map
 0)

=> 5889
```


```clojure
(defn create-n-nested-map [n]
  (reduce (fn [acc _] {:a acc}) {} (range n)))
```

```clojure
(flatten-paths (create-n-nested-map 1000000) "-")

=> {:a-a-a-a-a-a... {}}
```

This concludes this guide to flattening key paths in Clojure.
