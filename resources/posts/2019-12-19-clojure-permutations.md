Title: Clojure: permutations

I was solving an [Advent of Code](https://adventofcode.com/) problem and part of the solution required generating all the permutations of a set of numbers. Permutation is the act of arranging the members of a set into different orders. I wanted to write a recursive solution rather than an imperative solution. After some searching I found a [Scheme](https://en.wikipedia.org/wiki/Scheme_(programming_language)) implementation. As Scheme and Clojure are both dialects of Lisp translating one into the other shouldn't be too difficult.

The Scheme implementation:

```Clojure
(define (permutations l)
  (cond
   ((= (length l) 1) (list l))
   (else (apply append
                (map
                 (lambda (head)
                   (map
                    (lambda (tail) (cons head tail))
                    (permutations (remove head l))))
                 l)))))

(permutations '(1 2 3))
=> ((1 2 3) (1 3 2) (2 1 3) (2 3 1) (3 1 2) (3 2 1))
```

A first pass at the translation sees some minor changes: `length` to `count`, `lambda` to `fn`,`append` to `concat` and finally `remove` in Clojure takes a predicate rather than an element to remove. This gives us:

```Clojure
(defn permutations [l]
  (cond (= (count l) 1)
        (list l)
        :else
        (apply concat
               (map (fn [head]
                      (map (fn [tail]
                             (cons head tail))
                           (permutations (remove #{head} l))))
                    l))))

(permutations [1 2 3])
=> ((1 2 3) (1 3 2) (2 1 3) (2 3 1) (3 1 2) (3 2 1))
```

In Clojure when you have a nested `map` you can replace it with a `for` comprehension. With the first binding acting as the outer loop and the second binding as the inner loop. We've renamed `l` to `s` as the fundamental data structure in Clojure is the sequence and not the list:

```Clojure
(defn permutations [s]
  (if (= (count s) 1)
    (list s)
    (for [head s
          tail (permutations (remove #{head} s))]
      (cons head tail))))

(permutations [1 2 3])
=> ((1 2 3) (1 3 2) (2 1 3) (2 3 1) (3 1 2) (3 2 1))
```

We can make out recursive solution use a single stack frame by wrapping it in a lazy-seq:

```Clojure
(defn permutations [s]
  (lazy-seq
   (if (= (count s) 1)
     (list s)
     (for [head s
           tail (permutations (remove #{head} s))]
       (cons head tail)))))

(permutations [1 2 3])
=> ((1 2 3) (1 3 2) (2 1 3) (2 3 1) (3 1 2) (3 2 1))
```

We can replace `count` with `next` to make the solution more idiomatic:

```Clojure
(defn permutations [s]
  (lazy-seq
   (if (next s)
     (for [head s
           tail (permutations (remove #{head} s))]
       (cons head tail))
     [s])))

(permutations [1 2 3])
=> ((1 2 3) (1 3 2) (2 1 3) (2 3 1) (3 1 2) (3 2 1))
```

Our permutation function is working fine. However, when we pass in duplicate values we get an unexpected results:

```Clojure
(permutations '(2 2))
=> ((2) (2))
```

In this case we get `((2) (2))` but we expect to get `((2 2) (2 2))` as each `2` is a separate item despite having the same value. We can fix this by replacing `remove` with `remove-first`. Remove first removes the first item to match the predicate rather than all items:

```Clojure
(defn remove-first [pred s]
  (lazy-seq
   (when-let [[x & xs] (seq s)]
     (cond (pred x) xs
           :else   (cons x (remove-first pred xs))))))

(defn permutations [s]
  (lazy-seq
   (if (next s)
     (for [head s
           tail (permutations (remove-first #{head} s))]
       (cons head tail))
     [s])))

(permutations [2 2])
=> ((2 2) (2 2))
```

This works as intended assuming  we want our function to handle duplicate values. If we don't want our function to handle duplicate values we can make this explicit by passing in a set and using `disj` instead of `remove`:

```Clojure
(defn permutations [s]
  (lazy-seq
   (if (next s)
     (for [head s
           tail (permutations (disj s head))]
       (cons head tail))
     [s])))

(permutations #{1 2 3})
=> ((1 2 3) (1 3 2) (2 1 3) (2 3 1) (3 1 2) (3 2 1))
```

This implementation has a performance advantage over our version with `remove`. This is because `disj` has `O(log32N)` (almost `O(1)`) performance compared to `reduce` which has `O(N)` performance.

We now have a lazy recursive implementation of permutations that won't overflow the stack.

For more comprehensive collection of functions for permutations and combinations checkout the [clojure.math.combinatorics](https://github.com/clojure/math.combinatoricsh/) library.
