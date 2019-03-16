Title: Clojure: sorting

Sorting collections of items is something that comes up frequently in software development. This post covers the multitude of ways you can sort things in Clojure.

### Sorting numbers

To sort numbers we use the `sort` function.

```clojure
=> (sort [9 3 2 8 1 9])

(1 2 3 8 9 9)
```

To reverse the sort order we pass the `>` function into the `sort` function.

```clojure
=> (sort > [9 3 2 8 1 9])

(9 9 8 3 2 1)
```

### Sorting strings and dates

Sorting strings and dates in ascending order is exactly the same as sorting numbers.

```clojure
=> (sort ["b" "a" "f" "c" "x"])

("a" "b" "c" "f" "x")

=> (sort [#inst "2019-02-03"
          #inst "2019-05-03"
          #inst "2018-09-03"])

(#inst "2018-09-03T00:00:00.000-00:00"
 #inst "2019-02-03T00:00:00.000-00:00"
 #inst "2019-05-03T00:00:00.000-00:00")
```

However, passing the `>` function into the `sort` function gives us an error.

```clojure
=> (sort > ["b" "a" "f" "c" "x"])

Execution error (ClassCastException) at java.util.TimSort/countRunAndMakeAscending (TimSort.java:355).
java.lang.String cannot be cast to java.lang.Number

=> (sort > [#inst "2019-02-03"
            #inst "2019-05-03"
            #inst "2018-09-03"])

Execution error (ClassCastException) at java.util.TimSort/countRunAndMakeAscending (TimSort.java:355).
java.util.Date cannot be cast to java.lang.Number
```

To reverse the sort we need to make our own [comparator](https://clojure.org/guides/comparators). The easiest way to do this is with the `compare` function. By changing the argument order to the compare function we can change the order of the sort.

```clojure
(defn desc [a b]
  (compare b a))

=> (sort desc ["b" "a" "f" "c" "x"])

("x" "f" "c" "b" "a")

=> (sort desc [#inst "2019-02-03"
               #inst "2019-05-03"
               #inst "2018-09-03"])

(#inst "2019-05-03T00:00:00.000-00:00"
 #inst "2019-02-03T00:00:00.000-00:00"
 #inst "2018-09-03T00:00:00.000-00:00")
```

### Sorting with nil values

The `sort` function moves `nil` values to the front.

```clojure
=> (sort [2 6 nil 7 4])

(nil 2 4 6 7)
```

We can reverse this sort with the `desc` comparator we defined earlier. This, will move `nil` to the back but also reverse the sort.

```clojure
=> (sort desc [2 6 nil 7 4])

(7 6 4 2 nil)
```

If we want `nil` values to be at the back without changing the sort order, we can use the `juxt`, `nil?` and `identity` functions (for more on `juxt` check out [this post](https://andersmurphy.com/2018/11/18/clojure-juxt-and-separate.html)).

```clojure
=> (sort-by (juxt nil? identity) [2 6 nil 7 6])

(2 6 6 7 nil)
```

If we want to reverse the order but keep `nil` values at the front, we can pass in the `desc` comparator we defined earlier.

```clojure
=> (sort-by (juxt nil? identity) desc [2 6 nil 7 6])

(nil 7 6 6 2)
```

### Sorting maps by key

To sort maps by key we use the `sort-by` function, passing in the key we want to sort by as an argument.

```clojure
=> (sort-by :a [{:a 2 :b 1} {:a 3 :b 1} {:a 1 :b 2}])

({:a 1, :b 2} {:a 2, :b 1} {:a 3, :b 1})
```

Like `sort`, the `sort-by` function can also take a comparator to change the order of the sort.

```clojure
=> (sort-by :a > [{:a 2 :b 1} {:a 3 :b 1} {:a 1 :b 2}])

({:a 3, :b 1} {:a 2, :b 1} {:a 1, :b 2})
```

### Secondary sort

Secondary sorts can be achieved using the `juxt` function.

```clojure
=> (sort-by (juxt :a :b) [{:a 2 :b 1} {:a 1 :b 3} {:a 1 :b 2}])

({:a 1, :b 2} {:a 1, :b 3} {:a 2, :b 1})
```

### Sorting by count

If you want to sort by count, just pass the count function to `sort-by` (note: we pass it as the key function not the comparator).

```clojure
=> (sort-by count ["car" "airplane" "bike"])

("car" "bike" "airplane")
```

### Stable sort

Both `sort` and `sort-by` are stable, meaning equal elements will not be reordered.

```clojure
clojure.core/sort
([coll] [comp coll])
  Returns a sorted sequence of the items in coll. If no comparator is
  supplied, uses compare.  comparator must implement
  java.util.Comparator.  Guaranteed to be stable: equal elements will
  not be reordered.  If coll is a Java array, it will be modified.  To
  avoid this, sort a copy of the array.

clojure.core/sort-by
([keyfn coll] [keyfn comp coll])
  Returns a sorted sequence of the items in coll, where the sort
  order is determined by comparing (keyfn item).  If no comparator is
  supplied, uses compare.  comparator must implement
  java.util.Comparator.  Guaranteed to be stable: equal elements will
  not be reordered.  If coll is a Java array, it will be modified.  To
  avoid this, sort a copy of the array.
```

This is useful as it means it doesn't matter what order you apply your sorts to a collection. You can sort by the secondary sort and then sort by your primary sort and you won't lose the secondary sort order.

```clojure
=> (sort-by :b [{:a 2 :b 1} {:a 1 :b 3} {:a 1 :b 2}])

({:a 2, :b 1} {:a 1, :b 2} {:a 1, :b 3})

=> (sort-by :a [{:a 2, :b 1} {:a 1, :b 2} {:a 1, :b 3}])

({:a 1, :b 2} {:a 1, :b 3} {:a 2, :b 1})

```

Hopefully this covers most of your day to day sorting needs.
