Title: Clojure: merging maps by key (join)

We have two sequences of maps (tables) and we want to merge them on matching keys (relations).

```clojure
(def name-table [{:userid 1 :name "Bob"}
                 {:userid 2 :name "Jo"}
                 {:userid 3 :name "Sarah"}])

(def email-table [{:userid 1 :email "bob@email.com"}
                  {:userid 2 :email "jo@email.com"}
                  {:userid 3 :email "sarah@email.com"}])
```

### Using concat, group-by and merge

We can achieve this using `concat`, `gourp-by` and `merge`. We concatenate the two sequences together, group the maps by `:userid` key and then map over the grouped maps (rows) merging them.

```clojure
(defn merge-on-key
  "Merger rows of table1 and table2 on matching key k."
  [k table1 table2]
  (->> (concat table1 table2)
       (group-by k)
       (map val)
       (map (fn [[row1 row2]]
              (merge row1 row2)))))

=> (merge-on-key :userid
                 name-table
                 email-table)

({:userid 1, :name "Bob", :email "bob@email.com"}
 {:userid 2, :name "Jo", :email "jo@email.com"}
 {:userid 3, :name "Sarah", :email "sarah@email.com"})
 ```

This works for sequences of the same length, but what do we get if the sequences are different lengths (one of the tables has missing rows)?

 ```clojure
(def name-table [{:userid 1 :name "Bob"}
                 {:userid 2 :name "Jo"}
                 {:userid 3 :name "Sarah"}])

(def email-table [{:userid 1 :email "bob@email.com"}
                  {:userid 2 :email "jo@email.com"}])

=> (merge-on-key :userid
                 name-table
                 email-table)

({:userid 1, :name "Bob", :email "bob@email.com"}
 {:userid 2, :name "Jo", :email "jo@email.com"}
 {:userid 3, :name "Sarah"})
 ```

This returns all the user names with their corresponding emails if they have one. We effectively get a LEFT OUTER JOIN.

### Using clojure.set/join

If we just want all user names with corresponding emails, also known as an INNER JOIN, we can use the `join` function from the `clojure.set` namespace.

```clojure
(def name-table [{:userid 1 :name "Bob"}
                 {:userid 2 :name "Jo"}
                 {:userid 3 :name "Sarah"}])

(def email-table [{:userid 1 :email "bob@email.com"}
                  {:userid 2 :email "jo@email.com"}])

=> (clojure.set/join (set name-table) (set email-table))

({:userid 1, :name "Bob", :email "bob@email.com"}
 {:userid 2, :name "Jo", :email "jo@email.com"})
```

This just scratches the surface of the relational algebra you can do with Clojure data structures. For more check out the [clojure.set](https://clojuredocs.org/clojure.set) namespace.
