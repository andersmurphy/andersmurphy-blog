Title: Clojure: emoji in strings

Sometimes your problem domain requires the use of emoji. This article will cover how emoji are represented in Clojure strings.

## Emoji literal

We can represent emoji literally by copy pasting the emoji into your source code.

```clojure
"🔮"

=> "🔮"
```

## Using unicode

Another option is to use unicode. Clojure uses Java's string class which unfortunately doesn't have support for unicode literals. Instead we have to write our own function for converting unicode to strings.

```clojure
(defn unicode->string [code]
  (-> (Character/toChars code)
      String.))

(unicode->string 0x1F52E)

=> "🔮"
```

## Things to watch out for

It's worth bearing in mind that Java uses UTF-16 encoding. This means that unicode characters will use one 16-bit word or two 16-bits words, depending on the character. We can see this by using `count`.

```clojure
(count "🔮")

=> 2
```

The string is 2 characters long, even though it consists of only a single emoji. On the other hand 16-bit words mean you cant represent a two word emoji in a single character (char).

If we get the first or second character of this string we get a `?`.

```clojure
(subs "🔮 Estimating!" 0 1)

=> "?"

(subs "🔮 Estimating!" 1 2)

=> "?"
```

If we get the first two characters of the string we get the emoji.

```clojure
(subs "🔮 Estimating!" 0 2)

=> "🔮"
```

So keep the above in mind when manipulating strings containing emoji, you can easily get inconsistent/unexpected behaviour.

This concludes this brief introduction to emoji in Clojure.
