Title: Clojure: destructive macros

In this post we'll cover writing a macro that supports destructuring and does something with the bindings. More specifically we will write a macro that makes building maps from arbitrary data less verbose.

Consider the following code:

```Clojure
(let [{{:keys [year]}    :meta
       [{:keys [titles]}] :people}
      {:meta    {:year "1249"
                 :region "Estiria"}
       :people [{:titles ["Duke" "Silver Tongue"]
                 :name "John"}
                {:titles ["Queen" "Sun"]
                 :name "Jill"}
                {:titles ["Jarl" "Broken"]
                 :name "Vigo"}]}]
  {:year year :titles titles})

=>
{:year "1249", :titles ["Duke" "Silver Tongue"]}
```

This is fine. But, what we really want is to create a map with the keys that match the symbols we bind when destructuring. This will do away with the ceremony of building the map by hand `{:year year :titles titles}`.

`defmacro` and `destructure` to the rescue.

```Clojure
(destructure
 '[{{:keys [year]}    :meta
    [{:keys [titles]}] :people}
   {:meta    {:year "1249"
              :region "Estiria"}
    :people [{:titles ["Duke" "Silver Tongue"]
              :name "John"}
             {:titles ["Queen" "Sun"]
              :name "Jill"}
             {:titles ["Jarl" "Broken"]
              :name "Vigo"}]}])

[map__41580
 {:meta {:year "1249", :region "Estiria"},
  :people
  [{:titles ["Duke" "Silver Tongue"], :name "John"}
   {:titles ["Queen" "Sun"], :name "Jill"}
   {:titles ["Jarl" "Broken"], :name "Vigo"}]}
 map__41580
 (if
  (clojure.core/seq? map__41580)
  (clojure.lang.PersistentHashMap/create (clojure.core/seq map__41580))
  map__41580)
 map__41581
 (clojure.core/get map__41580 :meta)
 map__41581
 (if
  (clojure.core/seq? map__41581)
  (clojure.lang.PersistentHashMap/create (clojure.core/seq map__41581))
  map__41581)
 year
 (clojure.core/get map__41581 :year)
 vec__41582
 (clojure.core/get map__41580 :people)
 map__41585
 (clojure.core/nth vec__41582 0 nil)
 map__41585
 (if
  (clojure.core/seq? map__41585)
  (clojure.lang.PersistentHashMap/create (clojure.core/seq map__41585))
  map__41585)
 titles
 (clojure.core/get map__41585 :titles)]
```

Despite the noise we can see that `destructure` outputs a list of bindings and values. Some of theses are bindings that we care about `year` and `titles`. The others, which have generated names like `vec__41582` and `map__41585`, are references to the collections being destructured.

We can filter these out to get the bindings we care about for building our map.

```Clojure
(->> (destructure
      '[{{:keys [year]}    :meta
         [{:keys [titles]}] :people}
        {:meta    {:year "1249"
                   :region "Estiria"}
         :people [{:titles ["Duke" "Silver Tongue"]
                   :name "John"}
                  {:titles ["Queen" "Sun"]
                   :name "Jill"}
                  {:titles ["Jarl" "Broken"]
                   :name "Vigo"}]}])
     (partition 2)
     (remove (fn [[k]] (re-find #"^(vec__|map__)" (name k))))))

=>
((year   (clojure.core/get map__41611 :year))
 (titles (clojure.core/get map__41615 :titles)))
```

We can define a macro that uses a `map` and `into` to build a map with keys that match the bound symbols.

```Clojure
(defmacro let->map [bindings]
  (let [dest-bindings (destructure bindings)]
    (->> (partition 2 dest-bindings)
         (remove (fn [[k]]
                   (or (= k '_)
                       (re-find #"^(vec__|map__)" (name k)))))
         (mapv (fn [[k v]] [(keyword k) v]))
         (into {}))))

=>
Syntax error compiling at (/private/var/folders/ms/x72d2hr9487980y4_dpy7gym0000gn/T/form-init17784029688269253732.clj:1:1).
Unable to resolve symbol: map__41669 in this context
```

Looks like this error is caused because the compiler can't find `map__41669`. This is one of the bindings `destructure` generates. As we haven't bound `map__41669` when `(clojure.core/get map__41669 :year)` is called it throws an error.

We can fix this by binding all the of bindings outputed by `destructure`.

```Clojure
(defmacro let->map [bindings]
  (let [dest-bindings (destructure bindings)]
    `(let ~dest-bindings
       ~(->> (partition 2 dest-bindings)
             (remove (fn [[k]]
                       (or (= k '_)
                           (re-find #"^(vec__|map__)" (name k)))))
             (mapv (fn [[k v]] [(keyword k) v]))
             (into {})))))
```

Let's give it another go.

```Clojure
(let->map [{{:keys [year]}    :meta
              [{:keys [titles]}] :people}
             {:meta    {:year "1249"
                        :region "Estiria"}
              :people [{:titles ["Duke" "Silver Tongue"]
                        :name "John"}
                       {:titles ["Queen" "Sun"]
                        :name "Jill"}
                       {:titles ["Jarl" "Broken"]
                        :name "Vigo"}]}])

=>
{:year "1249", :titles ["Duke" "Silver Tongue"]}
```

It works!

What about with lists?

```Clojure
(map #(let->map [{name :name [first-title] :titles} %])
       [{:titles ["Duke" "Silver Tongue"]
         :name "John"}
        {:titles ["Queen" "Sun"]
         :name "Jill"}
        {:titles ["Jarl" "Broken"]
         :name "Vigo"}])

=>
({:name "John", :first-title "Duke"}
 {:name "Jill", :first-title "Queen"}
 {:name "Vigo", :first-title "Jarl"})
```

Magic.

In this post we've seen how to use `destructure` to write a macro that supports destructuring and does something useful with the bindings.

That being said. I'd probably think twice before using `let->map` everywhere as it's implementation is somewhat fragile (`filter` depending on collections starting with "map__" or "vec__") and is unlikely to cover all edge cases. The gains are also pretty minimal.
