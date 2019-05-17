Title: Clojure: case conversion and boundaries

Inconsistent case is a problems that tends to come up at application boundaries in your software stack. For example your Clojure codebase might use `kebab-case` for keywords, whilst your database uses `snake_case` for column names and your client wants `camelCase` in its json responses. Often, conventions and/or technical limitations prevent you from simply having a single case throughout your entire stack.

One "solution" to this problem is to accept the fact that your app will have a mix of cases. However, this can lead to mistake and frustration, does this function expect `customer-id`, `cutomerId` or `customer_id`? What format does our mobile client expect? A more practical solution to this problem, the one this article will cover, is to add automatic case conversion at these boundaries in your software stack.

### Converting the case of a key

Let's start by writing a simple case conversion function for converting `kebab-case` keywords to `camelCase` keywords.

```clojure
(defn kebab-case->camelCase [k]
  (let [words (clojure.string/split (name k) #"-")]
    (->> (map clojure.string/capitalize (rest words))
         (apply str (first words))
         keyword)))

=> (kebab-case->camelCase :foo-bar-baz)

:fooBarBaz
```

### Converting the case of keys in a map

Now that we have a function for converting case let's convert all the keys of a map using the `map-keys` function we implemented in [this article](https://andersmurphy.com/2018/11/10/clojure-map-values-and-keys.html).

```clojure
(defn map-keys [f m]
  (->> (map (fn [[k v]] [(f k) v]) m)
       (into {})))

=> (map-keys kebab-case->camelCase
             {:character-id 1 :first-name "John" :second-name "Snow"})

{:characterId 1, :firstName "John", :secondName "Snow"}
```

### Converting case of keys in a nested data structure

For converting the keys of arbitrarily nested data structures we can use the `clojure.walk/postwalk` function. Let's check out the docs.

```clojure
=> (doc clojure.walk/postwalk)

-------------------------
clojure.walk/postwalk
([f form])
  Performs a depth-first, post-order traversal of form.  Calls f on
  each sub-form, uses f's return value in place of the original.
  Recognizes all Clojure data structures. Consumes seqs as with doall.
```

Combining `clojure.walk/postwalk` with our `map-keys` function we can create a `transform-keys` function that will take a transformation function and apply it to all keys in a data structure.

```clojure
(defn transform-keys [t form]
  (clojure.walk/postwalk (fn [x] (if (map? x) (map-keys t x) x)) form))

=> (transform-keys kebab-case->camelCase
    [{:character-id 1
      :first-name "Olaf"
      :second-name "Iondrake"
      :items {:bag-of-holding ["sword" "axe" "money"]}}
     {:character-Id 2
      :first-name "Sigurd"
      :second-name "Rockfist"
      :items {:bag-of-holding ["scroll" "potion of healing"]}}])

[{:characterId 1,
  :firstName "Olaf",
  :secondName "Irondrake",
  :items {:bagOfHolding ["sword" "axe" "money"]}}
 {:characterId 2,
  :firstName "Sigurd",
  :secondName "Rockfist",
  :items {:bagOfHolding ["scroll" "potion of healing"]}}]
```

There you have it, a function for converting the case of keys in an arbitrarily nested data structure. You can use this function at the boundaries of your software stack to keep you and your team sane.

For more robust case conversion checkout the awesome [camel-snake-kebab](https://github.com/clj-commons/camel-snake-kebab) library.
