Title: Clojure: sorting a sequence based on another sequence

Sometimes web responses contain an unordered sequence of items along with a separate manifest that specifies the ordering. This article will cover how you can go about sorting that list of items by the manifest order as well as using [Clojure Spec](https://clojure.org/guides/spec#_exercise). to generate test data and verify the output.

### Sorting by manifest order

Let's start by writing some test data.

```clojure
(def manifest
  {:item-order ["x234" "d543" "g090" "z001" "a362"]})

(def items
  [{:id "z001" :type "food"}
   {:id "g090" :type "drink"}
   {:id "a362" :type "food"}
   {:id "x234" :type "drink"}
   {:id "d543" :type "food"}])
```

Now that we have some data we can use the `sort-by` function and the `.indexOf` method to make the order of the items match the order in the manifest.

```clojure
=> (sort-by (fn [{:keys [id]}]
              (.indexOf (manifest :item-order) id))
     items)

({:id "x234" :type "drink"}
 {:id "d543" :type "food"}
 {:id "g090" :type "drink"}
 {:id "z001" :type "food"}
 {:id "a362" :type "food"})
```

This seems to work. However, `.indexOf` gets called for every item in our list of items. Performance might suffer for large amounts of data. Let's write some performance tests and see whether we are right.

### Generating data

First we need to write a function that generates a large number of unique string ids. This is so that we can use it later to generate the same set of ids for our manifest and our items. We use the `shuffle` function to make sure every call to this function returns the result in a random order.

```clojure
(defn large-shuffled-vec-of-ids []
  (->> (range 1000)
       (map (partial str "xid"))
       shuffle
       vec))
```

We can then define our various specs using `s/def`. We use `s/with-gen` to provide a custom generator for our item-order and our item ids. We do this to ensure that both the manifest and the items have the same set of ids. We use a single item set as our spec because we always want the generator to return the same result.

```clojure
(require '[clojure.spec.alpha :as s])
(require '[clojure.spec.gen.alpha :as gen])

(s/def ::id string?)
(s/def ::item-order
  (s/with-gen
    (s/coll-of ::id :distinct true)
    #(s/gen #{(large-shuffled-vec-of-ids)})))
(s/def ::manifest (s/keys :req-un [::item-order]))
(s/def ::type #{"food" "drink"})
(s/def ::item (s/keys :req-un [::id ::type]))
```

For our `::items` spec we use `s/exercise` to generate as many items as we have ids. We `concat` the result because `s/exercise` returns pairs. We then use `map` and  `merge` to overwrite the random ids with the ids from our `large-shuffled-vec-of-ids` function.

```clojure
(s/def ::items
  (s/with-gen
    (s/coll-of ::item :distinct true)
    #(s/gen #{(let [ids (large-shuffled-vec-of-ids)]
                (map (fn [item id] (merge item {:id id}))
                     (apply concat (s/exercise ::item (count ids)))
                     ids))})))
```

We use `s/and` to add a `consistent-ids?` spec to ensure that our items and manifest share the same set of ids. This isn't strictly necessary as our `large-shuffled-vec-of-ids` function should ensure this, but it's always good to capture our intent in a spec as they also serve as valuable documentation.

```clojure
(defn consistent-ids? [{:keys [manifest items]}]
  (= (set (manifest :item-order))
     (set (map :id items))))

(s/def ::items-with-manifest
  (s/and
   (s/keys :req-un [::manifest ::items])
   consistent-ids?))
```

Now we can use the `::items-with-manifest` spec to generate a large amount of test data for our performance test.

```clojure
=> (gen/generate (s/gen ::items-with-manifest))

{:manifest
 {:item-order
  ["xid313"
   "xid544"
   "xid846"
   "xid351"
   "xid67"
   ...
   }}
```

Warning! The output is quite large and will flood your repl.

### Performance test

Now that we can generate loads of test data we can use it to test our initial implementation using the `time` function. It's worth noting we use `do` to avoid flooding the repl with output results.

```clojure
(def large-manifest (gen/generate (s/gen ::items-with-manifest)))

(defn index-of-sort [{:keys [items manifest]}]
  (sort-by (fn [{:keys [id]}]
             (.indexOf (manifest :item-order) id))
           items))

=> (time (do (index-of-sort large-manifest) nil))

"Elapsed time: 561.437815 msecs"
```

As suspected our function is very slow for our large input. This is because each call of `.indexOf` has `O(n)` complexity. We can avoid this cost by building a map of values to index. This can be done using `(iterate inc 0)` to generate a sequence of numbers starting from 0 and then using `zipmap` to map the numbers to the id values.

```clojure
(defn hash-map-index-sort [{:keys [items manifest]}]
  (let [value->index (-> (manifest :item-order)
                         (zipmap (iterate inc 0)))]
    (sort-by (fn [{:keys [id]}]
               (value->index id))
             items)))

=> (time (do (hash-map-index-sort large-manifest) nil))

"Elapsed time: 2.564776 msecs"
```

The results from `time` show this implementation is much faster than our initial implementation.

### Validation and generative testing

We can also spec our function using `s/fdef` and use the spec to create a generative test for our function. The `:args` key defines the input to our function (also used for generating the input test data). The `:ret` key defines the output data. Finally, the `:fn` key defines the relation between input and output that we want to validate; in this case we want to check that the order of the output items is the same as the order of ids in the input manifest.

```clojure
(require '[clojure.spec.test.alpha :as stest])

(s/fdef hash-map-index-sort
  :args (s/cat :m ::items-with-manifest)
  :ret  ::items
  :fn #(= (-> % :args :m :manifest :item-order)
          (->> % :ret (map :id))))

=> (-> (stest/check `hash-map-index-sort)
       first
       :clojure.spec.test.check/ret)

{:result true,
 :pass? true,
 :num-tests 1000,
 :time-elapsed-ms 14614,
 :seed 1558893244714}
```

This concludes our exploration of how to order items by a manifest as well as some use cases for Clojure Spec.
