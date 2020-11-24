Title: Clojure: previous, current and next

This article will cover a common pattern of iterating over a list of items whilst keeping a reference to the previous, current and next item.

Recently I had to implement link pagination for this blog. My initial attempt was rather naive:

```clojur
(defn link-pages
  [pages]
  (reduce
   (fn [pages next-page]
     (let [previous-page (first pages)]
       (if previous-page
         (conj
          (drop 1 pages)
          (assoc previous-page :next-page (:name next-page))
          (assoc next-page :previous-page (:name previous-page)))
         (conj pages next-page))))
   '()
   pages))
```

Using `conj` and an empty list to build a new list of pages that reference each other. It also had an inconvenient side effect of reversing the input list.

```clojur
(link-pages
  [{:name "page1"}
   {:name "page2"}
   {:name "page3"}
   {:name "page4"}
   {:name "page5"}])

=>
({:name "page5", :previous-page "page4"}
 {:name "page4", :previous-page "page3", :next-page "page5"}
 {:name "page3", :previous-page "page2", :next-page "page4"}
 {:name "page2", :previous-page "page1", :next-page "page3"}
 {:name "page1", :next-page "page2"})
```

I've since found a much more elegant way of implementing this:

```clojure
(defn link-pages
  [pages]
  (->> (concat [nil] pages [nil])
       (partition 3 1)
       (map (fn [[prev current next]]
              (cond-> current
                prev (assoc :previous-page (:name prev))
                next (assoc :next-page     (:name next)))))))
```

The core of this implementation revolves around the `partition` function which partitions a list into chunks (smaller lists) of n items (in this case 3). It can also be supplied an step which is the number of items to offset each chunk by. For example:

```clojure
(partition 3 1 [1 2 3 4 6 7 8 9])

=>
;; (prev current next)
((1 2 3) (2 3 4) (3 4 6) (4 6 7) (6 7 8) (7 8 9))
```

We use `nil` to denote the beginning and end of the list by adding it to the beginning and end of our initial input.

```clojure
(partition 3 1 [nil 1 2 3 4 6 7 8 9 nil])

=>
;; (prev current next)
((nil 1 2) (1 2 3) (2 3 4) (3 4 6) (4 6 7) (6 7 8) (7 8 9) (8 9 nil))
```

Finally `cond->` is just a nice way to conditionally do some things to a map (note that, unlike `cond`, `cond->` doesn't short circuit after the first true expression).

```clojur
(link-pages
  [{:name "page1"}
   {:name "page2"}
   {:name "page3"}
   {:name "page4"}
   {:name "page5"}])

=>
({:name "page1", :next-page "page2"}
 {:name "page2", :previous-page "page1", :next-page "page3"}
 {:name "page3", :previous-page "page2", :next-page "page4"}
 {:name "page4", :previous-page "page3", :next-page "page5"}
 {:name "page5", :previous-page "page4"})
```

Not only is this solution simpler, it no longer has the side effect of reversing the list.

The full example of the pagination logic for this blog can be found
[here](https://github.com/andersmurphy/andersmurphy-blog/blob/232e86a9e19098856d2b3fd902408fb1118440ff/src/core.clj#L208).
