Title: Clojure: adding compile time errors with macros

Clojure is a dynamic language. But, something you might not know is that unlike a lot of other dynamic languages it's not an interpreted language it's actually compiled. Even when programming at the REPL the Java Virtual Machine's (JVM) Just In Time (JIT) compiler compiles Clojure code to byte code before evaluating it. Combining this with macros which are evaluated at compile time allows us to add compile time errors to our code.

In this example we are going create a macro that will throw an error at compile time if we pass a key that isn't in the `valid-keys` set.

```Clojure
(def valid-keys #{:a :b :c})
```

The `key=?` macro uses `assert` to check whether `valid-key` is in `valid-keys`. Because we haven't quoted this part of the code it will be evaluated at compile time and throw an error if the assertion fails.

```Clojure
(defmacro key=?
  "Check if valid-key is equal to k. Throws compile time error if valid
  key is not in a pre-defined set of valid keys."
  [valid-key k]
  (assert (valid-keys valid-key)
          (str
           " is not a valid key.\n\n"
           "Valid  keys are: "
           valid-keys
           "\n"))
  `(= ~valid-key ~k))
```

Passing in an invalid key throws an informative compile time error message.

```Clojure
(defn foo [a]
  (key=? :d a))

=> Unexpected error macroexpanding key=? at (form-init734607845340377328.clj:2:3).
Assert failed:  is not a valid key.

Valid  keys are: #{:c :b :a}

(valid-keys valid-key)
```

Passing in a valid key compiles.

```Clojure
(defn bar [a]
  (key=? :c a))

=>
#'user/bar
```

That covers this short post on using macros to add compile time errors. This can be a really useful pattern for adding compile time checks for static inputs.
