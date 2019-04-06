Title: Clojure: contains? and some

Checking for containment, whether a collection contains an item, is a common task in software development. This post covers the ways you can check for containment in Clojure.

### Contains?

The `contains?` function springs to mind, lets try it out in the repl.

```clojure
=> (contains? {:a 1 :b 2 :c 3} :a)

true

=> (contains? {:a 1 :b 2 :c 3} :d)

false
```

So far so good, `contains?` works as expected with maps.

```clojure
=> (contains? #{:a :b :c} :a)

true

=> (contains? #{:a :b :c} :d)

false
```

Looks like it also works with sets.

```clojure
=> (contains? [1 :b  3 :a] :a)

false

=> (contains? [2 3 4] 1)

true

=> (contains? [1 3 4] 4)

false
```

What's going on here? We are not getting "expected" behaviour with vectors. Let's look at the docs.

```clojure
=> (doc contains?)
-------------------------
clojure.core/contains?
([coll key])
  Returns true if key is present in the given collection, otherwise
  returns false.  Note that for numerically indexed collections like
  vectors and Java arrays, this tests if the numeric key is within the
  range of indexes. 'contains?' operates constant or logarithmic time;
  it will not perform a linear search for a value.  See also 'some'.
```

So for vectors, `contains?` is only useful for checking if an array contains an index, not a value.

### Some

The documentation for `contains?` mentions the `some` function. Let's investigate the docs for `some`.

```clojure
=> (doc some)
-------------------------
clojure.core/some
([pred coll])
  Returns the first logical true value of (pred x) for any x in coll,
  else nil.  One common idiom is to use a set as pred, for example
  this will return :fred if :fred is in the sequence, otherwise nil:
  (some #{:fred} coll)
```

So we can use `some` to check if a collection contains an item that satisfies the supplied predicate.

```clojure
=> (some even? [1 2 3])

true

=> (some even? [1 3 5])

nil

=> (some identity [nil false nil 4 nil])

4
```

You can also use `some` to check for containment by passing in a set containing the value you want to check for, eg: `#{:a}`. This works because sets are functions in Clojure that return the value you pass into them if it is contained in the set and nil otherwise. Finally `some` can also be used for checking if a collection contains at least one of the values in the set, returning the first true value it encounters.

```clojure
=> (some #{:a} [1 :b :a 2])

:a

=> (some #{:d} [1 :b :a 2])

nil

=> (some #{:a :b} [1 :b :a 2])

:b
```

Hopefully this covers most of your day to day containment needs.
