Title: Clojure: string interpolation

We have a URL with some placeholder values that we want to replace.

```
"https://www.shop.com/$seller/items/$item-code/prices/$currency"
```

### str

One way of doing this is to use `str` function.

```clojure
(defn shop-url [seller item-code currency]
  (str "https://www.shop.com/" seller
       "/items/"               item-code
       "/prices/"              currency))

=> (shop-url "bob" "A567" "EUR")

"https://www.shop.com/bob/items/A567/prices/EUR"
```

There's nothing wrong with this solution. However, it does encapsulate/hide information that might be useful at the call site: order of arguments and what URL it is operating on.

### format

Another approach would be to use the `format` function, which gives us string interpolation.

```clojure
=> (format "https://www.shop.com/%s/items/%s/prices/%s"
           "bob"
           "A567"
           "EUR")

"https://www.shop.com/bob/items/A567/prices/EUR"
```

The downside with this is that the placeholders in the URL are not self-documenting.

### replace-several

What about using a string replace function? Clojure core does have functions for replacing matches in strings, but it doesn't have a built-in function for replacing multiple different matches. Let's see how we could implement our own `replace-several` function.

```clojure
(defn replace-several [s & {:as replacements}]
  (reduce (fn [s [match replacement]]
            (clojure.string/replace s match replacement))
          s replacements))

=> (replace-several "https://www.shop.com/$seller/items/$item-code/prices/$currency"
                    "$seller" "bob"
                    "$item-code" "A567"
                    "$currency" "EUR")

"https://www.shop.com/bob/items/A567/prices/EUR"
```

The above works and reads better than our previous solutions as the value substitutions are clear. If you find the destructuring in the `reduce` a bit cumbersome you can use `reduce-kv` which returns the accumulator, key and values for us.

```clojure
(defn replace-several [s & {:as replacements}]
  (reduce-kv clojure.string/replace s replacements))
```

Hopefully, these examples come in handy the next time you need some pseudo-string interpolation.
