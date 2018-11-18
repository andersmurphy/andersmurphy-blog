Title: Clojure: juxt and separate

Juxt is one of those higher order function that you never knew you needed. Despite not using it that often I find it can still be surprisingly useful. Let's check out the docs.

```clojure
=> (docs juxt)
-------------------------
clojure.core/juxt
([f] [f g] [f g h] [f g h & fs])
  Takes a set of functions and returns a fn that is the juxtaposition
  of those fns.  The returned fn takes a variable number of args, and
  returns a vector containing the result of applying each fn to the
  args (left-to-right).
  ((juxt a b c) x) => [(a x) (b x) (c x)]
```

So `juxt` takes a number of functions and returns a function that will apply each of those functions to its args.

### Sort-by multiple keys

Sort-by returns a sorted sequence of the items in a collection, where the sort
order is determined by comparing the key function of each item. If you want to have a secondary sort for when the first keys are equal you can use `juxt` to pass in two or more key functions, with each additional key being: a secondary, tertiary sort, etc.

```clojure
=> (sort-by (juxt :a :b)
     [{:a 3 :b 4} {:a 2 :b 2} {:a 2 :b 1}])

({:a 2 :b 1} {:a 2 :b 2} {:a 3 :b 4})
```

This can be handy when you need to sort items by criteria of decreasing importance.

### Group-by multiple keys

Group-by returns a map of elements grouped by key, where the grouping is determined by the supplied function. If you want to group by multiple functions you can use `juxt` to pass in two or more functions.

```clojure
(defn health-status [{:keys [health]}]
  (if (< health 50)
    :below-half-health
    :above-half-health))

=> (group-by
     (juxt :faction health-status)
           [{:name "Grug" :faction :orcs :health 49}
            {:name "Elendil" :faction :elves :health 98}
            {:name "Gazkral" :faction :orcs :health 65}
            {:name "Varok" :faction :orcs :health 31}
            {:name "Gork" :faction :orcs :health 29}])

{[:orcs :below-half-health]
 [{:name "Grug" :faction :orcs :health 49}
  {:name "Varok" :faction :orcs :health 31}
  {:name "Gork" :faction :orcs :health 29}]
 [:elves :above-half-health]
 [{:name "Elendil" :faction :elves :health 98}]
 [:orcs :above-half-health]
 [{:name "Gazkral" :faction :orcs :health 65}]}

(defn cleave-attack [actors]
  (dissoc actors [:orcs :below-half-health]))

=> (cleave-attack
    {[:orcs :below-half-health]
     [{:name "Grug" :faction :orcs :health 49}
      {:name "Varok" :faction :orcs :health 31}
      {:name "Gork" :faction :orcs :health 29}]
     [:elves :above-half-health]
     [{:name "Elendil" :faction :elves :health 98}]
     [:orcs :above-half-health]
     [{:name "Gazkral" :faction :orcs :health 65}]})

{[:elves :above-half-health]
 [{:name "Elendil", :faction :elves, :health 98}]
 [:orcs :above-half-health]
 [{:name "Gazkral", :faction :orcs, :health 65}]}
```

Now we know how many orcs will get cut down by Elendil's cleave attack.

### Separate

There was once a function in the now deprecated `clojure.contrib.seq-utils` library called `separate`. It would return a vector containing a sequence of the items that satisfied the predicate followed by a sequence of items that didn't satisfy the predicate. We can recreate this helpful function without `juxt` like so:

```clojure
(defn separate [pred s]
  [(filter pred s) (remove pred s)])

=> (separate odd? [1 2 3 2 1])

[(1 3 1) (2 2)]
```

However, we can make this more succinct with `juxt`. Removing the need to pass the same arguments into two different functions.

```clojure
(defn separate [pred s]
  ((juxt filter remove) pred s))

=> (separate odd? [1 2 3 2 1])

[(1 3 1) (2 2)]
```
Hopefully these examples give you a taste of what `juxt` can do.
