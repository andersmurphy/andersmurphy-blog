Title: Clojure: personalising text

Sometimes you want to make a user's experience feel more personal. An easy way to achieve this is by personalising text based content. For example in a text base adventure game you could replace placeholders in the text with information relevant to that particular player such as their name or class. This could help make your game more engaging.

## Personalising a string

Let's start by writing a simple function for personalising strings.

```clojure
(defn personalise [personalisations initial-string]
  (reduce (fn [s [k v]]
            (clojure.string/replace s (str k) v))
          initial-string
          personalisations))

(personalise {:class "warrior" :name "Ivan"}
             "The :class called :name entered the dungeon.")

=> "The warrior called Ivan entered the dungeon."
```

## Personalising the string values of a map

Now that we have a function for personalising strings let's convert all the string values of a map using the `map-values` function we implemented in [this article](https://andersmurphy.com/2018/11/10/clojure-map-values-and-keys.html).

```clojure
(defn map-values [f m]
    (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

(map-values (partial personalise
                     {:class "warrior" :name "Ivan"})
            {:journal ":name's journal"
            :weapon  "The :class's sword"})

=> {:journal "Ivan's journal"
    :weapon  "The warrior's sword"}
```

This works for strings but what about none homogeneous maps?

```clojure
(defn map-values [f m]
    (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

(map-values (partial personalise {:class "warrior" :name "Ivan"})
            {:journal ":name's journal"
             :weapon  "The :class's sword"
             :treasures-found 1})

=> {:journal         "Ivan's journal"
    :weapon          "The warrior's sword"
    :treasures-found "1"}
```

Although this doesn't throw any exceptions, if we look closely it has caused unexpected behaviour. The number of treasures found has been converted to a string. Let's change the personalise function to prevent this unexpected behaviour from happening.

```clojure
(defn safe-personalise [personalisations initial-string]
  (if (string? initial-string)
    (reduce (fn [s [k v]]
              (clojure.string/replace s (str k) v))
            initial-string
            personalisations)
    initial-string))

(map-values (partial safe-personalise
                     {:class "warrior" :name "Ivan"})
            {:journal         ":name's journal"
             :weapon          "The :class's sword"
             :treasures-found 1})

=> {:journal         "Ivan's journal"
    :weapon          "The warrior's sword"
    :treasures-found 1}
```

## Personalising strings in a nested data structure

To apply a function to all the nodes of an arbitrarily nested data structures we can use the `clojure.walk/postwalk` function. Here are the docs.

```clojure
(doc clojure.walk/postwalk)

=>
-------------------------
clojure.walk/postwalk
([f form])
  Performs a depth-first, post-order traversal of form.  Calls f on
  each sub-form, uses f's return value in place of the original.
  Recognizes all Clojure data structures. Consumes seqs as with doall.
```

Combining `clojure.walk/postwalk` with our `safe-personalise` function we can personalise all the strings in a nested data structure.

```clojure
(clojure.walk/postwalk
 (partial safe-personalise {:class "warrior" :name "Ivan"})
  [{:item    ":name's bag of holding"
    :contain {:items ["The :class's sword"
                      "The :class's shield"]}
    :id      1}
   {:item ":name elven cloack"
    :id   2}])

=> [{:item "Ivan's bag of holding"
     :contain {:items ["The warrior's sword" "The warrior's shield"]}
     :id 1}
    {:item "Ivan elven cloack" :id 2}]
```

There we have it personalised text for our text based adventure.
