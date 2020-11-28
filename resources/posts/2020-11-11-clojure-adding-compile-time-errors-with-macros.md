Title: Clojure: adding compile time errors with macros

Clojure is a dynamic language. But, something you might not know is that unlike a lot of other dynamic languages it's not an interpreted language it's actually compiled. Even when programming at the REPL the Java Virtual Machine's (JVM) Just In Time (JIT) compiler compiles Clojure code to byte code before evaluating it. Combining this with macros which are evaluated at compile time allows us to add compile time errors to our code.

In this example we are going create a macro that will throw an error at compile time if we pass a key that isn't in the `valid-keys` set.

```Clojure
(def valid-keys #{:a :b :c})
```

The `validate-key-at-compile-time` macro uses `assert` to check whether `valid-key` is in `valid-keys`. Because we haven't quoted this part of the code it will be evaluated at compile time and throw an error if the assertion fails.

```Clojure
(defmacro validate-key-at-compile-time
  "Check if k is valid. Throws compile time error if k
  is not in a pre-defined set of valid keys."
  [k]
  (assert (valid-keys k)
          (str
           k
           " is not a valid key.\n\n"
           "Valid  keys are: "
           valid-keys
           "\n"))
  k)
```

Passing in an invalid key throws an informative compile time error message.

```Clojure
(defn foo []
  (validate-key-at-compile-time :d))

=>
Unexpected error macroexpanding compile-time-valid-key at (form-init18033988220353259505.clj:2:3).
Assert failed: :d is not a valid key.

Valid  keys are: #{:c :b :a}

(valid-keys k)
```

Passing in a valid key compiles.

```Clojure
(defn bar []
  (validate-key-at-compile-time :c))

=>
#'user/bar
```

Let's try a more complex use case where we define the key in a def.

```Clojure
(def k :c)

(defn bar []
  (validate-key-at-compile-time k))

=>
Unexpected error macroexpanding key=? at (form-init18033988220353259505.clj:2:3).
Assert failed: k is not a valid key.

Valid  keys are: #{:c :b :a}

(valid-keys k)
#'user/bar
```

This fails because we are passing in a symbol at compile time (macro expand time). When we look that symbol up in the set of valid keys it's not found.

To fix this we need to `resolve` the symbol first. The symbol resolves to the var `k` which we can then dereference with `@` to get the value `:c`.

```Clojure
(defmacro validate-key-at-compile-time
  "Check if k is valid. Throws compile time error if k
  is not in a pre-defined set of valid keys."
  [k]
  (let [resolved-k (if (symbol? k) @(resolve 'k) k)]
    (assert
     (valid-keys resolved-k)
     (str
      resolved-k
      " is not a valid key.\n\n"
      "Valid keys are: "
      valid-keys
      "\n")))
  k)
```

Now the code compiles correctly.

```Clojure
(def k :c)

(defn bar []
  (validate-key-at-compile-time k))
```

And throws and error when k is not a valid key.

```Clojure
(def k :d)

(defn bar []
  (validate-key-at-compile-time k))

=>
Unexpected error macroexpanding key=? at (form-init18033988220353259505.clj:2:3).
Assert failed: :d is not a valid key.

Valid keys are: #{:c :b :a}

(valid-keys resolved-k)
```

That covers this short post on using macros to add compile time errors. This can be a really useful pattern for adding compile time checks for static inputs.
