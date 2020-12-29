Title: Clojure: adding dissoc-in to our cond-merge macro

In the previous post we created a macro called `cond-merge` to conditionally associate in values to a map. In this post we will cover adding disassociation (removal of items) to this macro.

Lets start with the code we had at the end of the previous post:

```Clojure
(defn all-paths [m]
  (letfn [(all-paths [m path]
            (lazy-seq
             (when-let [[[k v] & xs] (seq m)]
               (cond (and (map? v) (not-empty v))
                     (into (all-paths v (conj path k))
                           (all-paths xs path))
                     :else
                     (cons [(conj path k) v]
                           (all-paths xs path))))))]
    (all-paths m [])))

(defmacro cond-merge
  [m1 m2]
  (assert (map? m2))
  (let [path-value-pairs (all-paths m2)
        sym-pairs     (map (fn [pair] [(gensym) pair]) path-value-pairs)
        let-bindings     (mapcat (fn [[sym [_ v]]] [sym v]) sym-pairs)
        conditions       (mapcat (fn [[sym [path _]]] [`(not (nil? ~sym))
                                                       `(assoc-in ~path ~sym)])
                                 sym-pairs)]
    `(let [~@let-bindings] (cond-> ~m1 ~@conditions))))
```

Before we can add disassociation behaviour to our macro we need to implement a convenience function for `dissoc-in` as there isn't one that comes with Clojure. The semantics of `dissoc-in` are not as obvious as you first think once you take into account data structures other than maps (see this [commentary from Alex Miller](https://ask.clojure.org/index.php/730/missing-dissoc-in?start=0#a_list_title)).

```Clojure
(defn dissoc-in [m ks]
  (let [v    (vec ks)
        path (pop v)
        k    (peek v)]
    (update-in m path dissoc k)))
```

But this leaves vestigial paths behind.

```Clojure
(dissoc-in {:k {:b {:c 1}}
              :a 1}
             [:k :b :c])

=>
{:k {:b {}}, :a 1}
```

We can implement a recursive version that resolves this issue. This version is doesn't use any stack frame optimisation (recur/lazy-seq). `assoc-in` has a similar implementation and if it's good enough for `assoc-in` then it's good enough for `assoc-dissoc`. I imagine the reasoning is that in practice you will rarely encounter a map that has enough layers of nesting to overflow the stack.

```Clojure
(defn dissoc-in
  [m [k & ks]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))
```

This implementation gives us a much cleaner result.

```Clojure
(dissoc-in {:k {:b {:c 1}}
              :a 1}
             [:k :b :c])

=>
{:a 1}
```

Technically, `dissoc-in` won't handle vectors like `assoc-in`:

```Clojure
(assoc-in {:k {:b {:c 1}
               :a [1 3]}}
          [:k :a 0] 39)

=>
{:k {:b {:c 1}, :a [39 3]}}

(dissoc-in {:k {:b {:c 1}
                :a [1 3]}}
             [:k :a 0])

=>
Execution error (ClassCastException) at
user/dissoc-in (form-init7710425534552485987.clj:10).
class clojure.lang.PersistentVector cannot be cast to class clojure.lang.IPersistentMap (clojure.lang.PersistentVector and clojure.lang.IPersistentMap are in unnamed module of loader 'app')
```

Thankfully, for this macro we are trying to follow merge semantics. In a `merge` vectors overwrite other vectors. But it something to keep in mind when implementing a macro as semantics can get confusing very quickly as you move away from prior art.

```Clojure
(defn assoc-or-dissoc [m sym path]
  (if (= sym :dissoc)
    (dissoc-in m path)
    (assoc-in  m path sym)))

(defmacro cond-merge [m1 m2]
  (assert (map? m2))
  (let [path-value-pairs (all-paths m2)
        sym-pairs     (map (fn [pair] [(gensym) pair]) path-value-pairs)
        let-bindings     (mapcat (fn [[sym [_ v]]] [sym v]) sym-pairs)
        conditions       (mapcat (fn [[sym [path _]]]
                                   [`(not (nil? ~sym))
                                    `(assoc-or-dissoc ~sym ~path)])
                                 sym-pairs)]
    `(let [~@let-bindings]
       (cond-> ~m1
         ~@conditions))))
```

We can now use the `:dissoc` keyword to remove items when we use `cond-merge`:

```Clojure
(def heavy-ship-data
  {:ship-class "Heavy"
   :name  "Thunder"
   :main-systems {:engine {:type "Ion"}}})

(defn ready-ship-cond-merge
  [{class :ship-class :as ship-data
    {{engine-type :type} :engine} :main-systems}]
  (cond-merge
   ship-data
   {:main-systems {:turret  {:type "Auto plasma incinerator"}
                   :engine  {:type :dissoc
                             :upgrade "Neutron spoils"
                             :fuel    (when (= engine-type "Flux")
                                        "Fusion cells")}
                   :shield  {:type (when (= class "Heavy")
                                     "Heavy shield")}}
    :name (if (= engine-type "Flux") "Fluxmaster" :dissoc)}))

(ready-ship-cond-merge heavy-ship-data)

=>
{:ship-class "Heavy",
 :main-systems
 {:engine {:upgrade "Neutron spoils"},
  :shield {:type "Heavy shield"},
  :turret {:type "Auto plasma incinerator"}}}
```

If you are concerned about a potential name collision with `:dissoc` (for example if your existing data has the `:dissoc` as a value) then you can use a namespace keywords:

```Clojure
(defn assoc-or-dissoc [m sym path]
  (if (= sym ::dissoc)
    (dissoc-in m path)
    (assoc-in  m path sym)))
```

This assumes that the macro will be implemented in its own namespace. You would then use it like this:

```Clojure
(require  '[project-name.util-lib :as lib]))

(defn ready-ship-cond-merge
  [{class :ship-class :as ship-data
    {{engine-type :type} :engine} :main-systems}]
  (lib/cond-merge
   ship-data
   {:main-systems {:turret  {:type "Auto plasma incinerator"}
                   :engine  {:type ::lib/dissoc
                             :upgrade "Neutron spoils"
                             :fuel    (when (= engine-type "Flux")
                                        "Fusion cells")}
                   :shield  {:type (when (= class "Heavy")
                                     "Heavy shield")}}
    :name (if (= engine-type "Flux") "Fluxmaster" ::lib/dissoc)}))
```

In this post we've seen how to extend our `cond-merge` macro to support disassociation by using a keyword and rewriting it in a macro.
