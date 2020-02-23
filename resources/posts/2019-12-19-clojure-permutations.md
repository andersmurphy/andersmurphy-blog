Title: Clojure: permutations

I was solving an [Advent of Code](https://adventofcode.com/) problem and part of the solution required generating all the permutations of a set of numbers. Permutation is the act of arranging the members of a set into different orders. I wanted to write a recursive solution rather than an imperative solution. After some searching I found a [Scheme](https://en.wikipedia.org/wiki/Scheme_programming_language) implementation. As Scheme and Clojure are both dialects of Lisp, translating one into the other wasn't too difficult.

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

A first pass at the translation sees some minor changes: `length` to `count`, `lambda` to `fn`, `append` to `concat` and finally `remove` in Clojure takes a predicate rather than an element to remove. This gives us:

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

In Clojure, a nested `map` can be replaced with a `for` comprehension; with the first binding acting as the outer loop and the second binding as the inner loop. `l` has been renamed to `s` as the fundamental data structure in Clojure is the sequence and not the list:

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

This recursive solution can be made to use a single stack frame by wrapping it in a `lazy-seq`:

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

`count` is replaced with `next` to make the solution more idiomatic:

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

The permutation function is working fine. However, when duplicate values are passed, there are unexpected results:

```Clojure
(permutations '(2 2))
=> ((2) (2))
```

In this case the output is `((2) (2))`, but the expected output should be `((2 2) (2 2))`, as each `2` is a separate item despite having the same value. This can be fixed by replacing `remove` with `remove-first`. This function removes the first element that matches the predicate rather than all elements that match it:

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

This works as intended assuming it is desirable for the function to handle duplicate values. If this is not the case, this can be made explicit by passing in a set and using `disj` instead of `remove`:

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

This implementation also has a performance advantage over the previous version with `remove`. This is because `disj` has `O(log32N)` (almost `O(1)`) performance compared to `remove` which has `O(N)` performance.

A more comprehensive collection of functions for generating permutations and combinations can be found in the [clojure.math.combinatorics](https://github.com/clojure/math.combinatoricsh/) library.
