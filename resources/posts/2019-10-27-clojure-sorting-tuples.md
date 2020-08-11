Title: Clojure: sorting tuples

A tuple is a finite ordered sequence of elements. A common use of tuples in Clojure is for representing pairs of data that are related; for example a key and a value when mapping over a map (hashmap/dictionary). That being said, tuples can be of any length and are a common way of representing larger sets of related data in languages that don't use maps as prolifically as Clojure. This articles explores sorting tuples in Clojure.

## Sorting same length tuples

If the tuples are the same length, they are sorted by a lexical sort; with the first item used for the primary sort, the second item for the secondary sort etc.

```clojure
(sort [[2 3] [1 2] [1 1]])

=> ([1 1] [1 2] [2 3])
```

The default comparator will throw an error if elements of different types need to be compared:

```clojure
(sort [[2 3] [1 "w"] [1 1]])

=>
Execution error (ClassCastException) at java.util.TimSort/countRunAndMakeAscending (TimSort.java:356).
class java.lang.String cannot be cast to class java.lang.Number
```

## Sorting different length tuples

If the tuples are of different lengths the default sort will be based on the length of the tuples rather than a lexical sort of their contents, falling back on the lexical sort for tuples of the same length:

```clojure
(sort [[2 3] [1 2 1] [1 1] [1 0]])

=> ([1 0] [1 1] [2 3] [1 2 1])
```

## Sorting different length tuples by their contents

If we want different length tuples to be sorted by a lexical sort of their contents, we need to write our own comparator:

```clojure
(defn tuple-compare [a b]
  (let [[x & xs] (seq a)
        [y & ys] (seq b)]
    (let [c (compare x y)]
      (cond
        (nil? x) c
        (#{1 -1} c) c
        :else       (recur xs ys)))))

(sort tuple-compare [[1 1] [2] [3 1] [0 1 2] [2]])

=> ([0 1 2] [1 1] [2] [2] [3 1])
```

This comparator recursively compares each item in the tuple using the `compare` function.

This concludes this guide to sorting tuples in Clojure. For more articles on sorting in Clojure check out: [Clojure: sorting](https://andersmurphy.com/2019/03/09/clojure-sorting.html) and [Clojure: sorting a sequence based on another sequence](https://andersmurphy.com/2019/05/25/clojure-sorting-a-sequence-based-on-another-sequence.html).
