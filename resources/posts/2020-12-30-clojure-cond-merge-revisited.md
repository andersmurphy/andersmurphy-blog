Title: Clojure: cond-merge revisited

In [this post](https://andersmurphy.com/2020/12/27/clojure-cond-deep-merge-remove-nils-and-the-shape-of-data.html) we created a macro called `cond-merge` to conditionally associate in values to a map. In this post we will revisit some of the limitations of `cond-merge` when it comes to nested conditionals and conditionals that return maps that can lead to overwriting data rather than merging data.

Here's an example of the problem:

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

If we revisit our macro code:

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

(defmacro cond-merge [m1 m2]
    (assert (map? m2))
    (let [path-value-pairs (all-paths m2)
          sym-pairs     (map (fn [pair] [(gensym) pair]) path-value-pairs)
          let-bindings  (mapcat (fn [[sym [_ v]]] [sym v]) sym-pairs)
          conditions    (mapcat (fn [[sym [path _]]]
                                  [`(not (nil? ~sym)) `(assoc-in ~path ~sym)])
                                sym-pairs)]
      `(let [~@let-bindings]
         (cond-> ~m1
           ~@conditions))))
```

If we macroexpand the code we should be able to see why we are getting this overwrite behaviour:

```Clojure
(macroexpand-1
   '(cond-merge {:a 1
                 :b {:e 3}}
                {:b (when true {:c 1 :d 2})
                 :c false}))

=>
(clojure.core/let
 [G__43628 (when true {:c 1, :d 2}) G__43629 false]
 (clojure.core/cond->
  {:a 1, :b {:e 3}}
  (clojure.core/not (clojure.core/nil? G__43628))
  (clojure.core/assoc-in [:b] G__43628)
  (clojure.core/not (clojure.core/nil? G__43629))
  (clojure.core/assoc-in [:c] G__43629)))
```

We can see that the value of `G__43628` is `(when true {:c 1, :d 2})` which means when that we will get `(assoc-in {:a 1, :b {:e 3}} [:b] {:c 1, :d 2})`. This explains why the pair `:e 3` get overwritten. To solve this at macroexpand time we would need to evaluate the expression `(when true {:c 1 :d 2})` at compile time to see whether it returns a map. While it would be possible to support a subset of functions like `when` and `if` and see whether they return map literals in their branches, it would not be possible to support complex functions with runtime input that may evaluate to maps.

Fundamentally this problem is much easier to solve at runtime, the realm of functions. So let's revisit our plain function solution by first looking at the source of `merge-with`.

```Clojure
(clojure.repl/source merge-with)

=>
  (defn merge-with
    "Returns a map that consists of the rest of the maps conj-ed onto
  the first.  If a key occurs in more than one map, the mapping(s)
  from the latter (left-to-right) will be combined with the mapping in
  the result by calling (f val-in-result val-in-latter)."
    {:added "1.0"
     :static true}
    [f & maps]
    (when (some identity maps)
      (let [merge-entry (fn [m e]
                                (let [k (key e) v (val e)]
                                  (if (contains? m k)
                                    (assoc m k (f (get m k) v))
                                    (assoc m k v))))
            merge2 (fn [m1 m2]
                         (reduce1 merge-entry (or m1 {}) (seq m2)))]
        (reduce1 merge2 maps))))
```

In the previous post we found our function solution had a massive performance cost. Most likely because it used `clojure.walk/postwalk` which is expensive performance-wise. If we could write our own version of `merge-with` which handled `nil` values on the first pass we should be able get similar performance to `merge-with`.

Here's our new function version of `cond-merge` which is effectively `merge-with` with some minor changes:


```Clojure
(defn cond-merge
  [& maps]
  (when (some identity maps)
    (letfn [(merge-e [m e]
              (let [k (key e) v (val e)]
                (cond
                  (nil? v) m
                  (map? v) (let [v (cond-merge (k m) v)]
                             (if (seq v)
                               (assoc m k v)
                               m))
                  :else    (assoc m k v))))
            (merge2 [m1 m2]
              (reduce merge-e (or m1 {}) m2))]
      (reduce merge2 maps))))
```

Our new function produces the expected results:

```Clojure
(cond-merge {:a 1
             :b {:e 3}
             :d {:e {:f 2}}}
            {:b (when true {:c 1 :d 2})
             :c false
             :d {:e {:f (when false 1)}}
             :e {:a (when false 1)}
             :a (when false 1)})

=>
{:a 1
 :b {:e 3 :c 1 :d 2}
 :c false}
```

It also handles empty maps and overwriting vectors:

```Clojure
(cond-merge {:a 1
             :b {:e 3}
             :d {:e {:f 2}}
             :x [1]}
            {:b (when true {:c 1 :d 2})
             :c false
             :d {:e {:f (when false 1)}}
             :e {:a (when false 1)}
             :a (when false 1)
             :y {:a 1
                 :b {:e 3}
                 :d {:e {:f 2}}}
             :z []
             :n {}
             :x []})

=>
{:a 1,
 :b {:e 3, :c 1, :d 2},
 :d {:e {:f 2}},
 :x [],
 :y {:a 1, :b {:e 3}, :d {:e {:f 2}}},
 :z [],
 :c false}
```

The performance is not bad:

```Clojure
(require '[criterium.core :as c])

(c/bench
 (cond-merge-old {:a 1
                  :b {:e 3}
                  :d {:e {:f 2}}
                  :x [1]}
                 {:b {:c (when true 1)
                      :d (when true 2)}
                  :c false
                  :d {:e {:f (when false 1)}}
                  :e {:a (when false 1)}
                  :a (when false 1)
                  :y {:a 1
                      :b {:e 3}
                      :d {:e {:f 2}}}
                  :z []
                  :n {}
                  :x []}))

=>
...
Execution time mean : 3.950938 µs
...
```

We can improve performance by moving to a hybrid approach:

```Clojure
(defn if-map-cond-merge [m path s]
  (if (map? s)
    (cond-merge m (assoc-in {} path s))
    (assoc-in m path s)))

(defmacro cond-merge-hybrid
  [m1 m2]
  (assert (map? m2))
  (let [path-value-pairs (all-paths m2)
        symbol-pairs     (map (fn [pair] [(gensym) pair]) path-value-pairs)
        let-bindings     (mapcat (fn [[s [_ v]]] [s v]) symbol-pairs)
        conditions       (mapcat (fn [[s [path _]]] [`(not (nil? ~s))
                                                     `(if-map-cond-merge ~path ~s)])
                                 symbol-pairs)]
    `(let [~@let-bindings] (cond-> ~m1 ~@conditions))))

(c/bench
 (cond-merge-hybrid {:a 1
                     :b {:e 3}
                     :d {:e {:f 2}}
                     :x [1]}
                    {:b {:c (when true 1)
                         :d (when true 2)}
                     :c false
                     :d {:e {:f (when false 1)}}
                     :e {:a (when false 1)}
                     :a (when false 1)
                     :y {:a 1
                         :b {:e 3}
                         :d {:e {:f 2}}}
                     :z []
                     :n {}
                     :x []}))

=>
...
Execution time mean : 1.948614 µs
...
```

Personally, I don't think the performance gain warrants the additional complexity of the macro. That being said it's nice to be able to fall back to it if need be. After all a 1.5-2x performance increase can make a big win if it's a bottleneck. Worth keeping in mind that the performance characteristics might be radically different with much larger data sets/nesting.

In this post we've seen that macros aren't always a straightforward solution and come with their own sets of trade offs.
