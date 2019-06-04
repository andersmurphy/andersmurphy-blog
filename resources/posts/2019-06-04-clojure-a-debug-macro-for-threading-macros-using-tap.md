Title: Clojure: a debug macro for threading macros using tap>

In this article we will cover how to make a debug macro using tap. See [this article](https://andersmurphy.com/2019/06/01/clojure-intro-to-tap-and-accessing-private-vars.html) for an introduction to Clojure 1.10's tap system.

### Setting up tap>

First we register a handler function with `add-tap` that writes whatever we pass into `tap>` to the `debug` atom.

```clojure
(def debug (atom []))

(defn add-to-debug [x]
  (swap! debug conj x))

(add-tap add-to-debug)

=> (tap> (map inc [1 2 3 4 5]))

true

=> @debug

[(2 3 4 5 6)]
```

When we dereference `debug` we get the result of evaluating `(map inc [1 2 3 4 5])`. This seems to works at the top level of our code, but what happens when we call tap in the middle of a nested expression?

```clojure
=> (take 1 (tap> (map inc [1 2 3 4 5])))

Error printing return value (IllegalArgumentException) at clojure.lang.RT/seqFrom (RT.java:552).
Don't know how to create ISeq from: java.lang.Boolean
```

We get an error as `tap>` doesn't return the result of the value we pass into it but the value of `true` instead.

### Writing debug* as a function

Let's write a simple function that writes our result to tap and then returns the result of the function to the expression that is calling tap.

```clojure
(reset! debug [])

(defn debug* [args]
  (do (tap> args)
      args))

=> (take 1 (debug* (map inc [1 2 3 4 5])))

(2)

=> @debug

[(2 3 4 5 6)]
```

This works. But it would be more helpful if we knew what code lead to that result.

```clojure
(reset! debug [])

(defn debug* [args]
  (do (tap> (sorted-map :fn args :ret args))
      args))

=> (take 1 (debug* (map inc [1 2 3 4 5])))

(2)

=> @debug

[{:fn (2 3 4 5 6), :ret (2 3 4 5 6)}]
```

Not quite. We want the value of `:fn` to be our code before it gets evaluated not the result after. Whenever you want to do something with code as data rather than the result of it's evaluation you need to use macro.

### Rewriting debug* as a macro

Rewriting our debug function as a macro is relatively straight forwards we change `defn` to `defmacro`, syntax quote the `do` form with `` ` `` , and unquote the `args` with `~`. Finally, we use `quote` to prevent the `args` from being evaluated.


```clojure
(reset! debug [])

(defmacro debug* [args]
  `(do
     (tap> (sorted-map :fn (quote ~args) :ret ~args))
     ~args))

=> (take 1 (debug* (map inc [1 2 3 4 5])))

(2)

=> @debug

[{:fn (map inc [1 2 3 4 5]), :ret (2 3 4 5 6)}]
```

Much better.

### Writing the debug->> macro

Next let's write a `debug->>` macro that will write each step to the `debug` atom. The `repeat` function generates a sequence of `'debug*` symbols which we `interleave` with the functions `fns` that have been passed into our macro. Finally we `~@` to splice (think apply) the resulting list into the regular `->>` macro.

```clojure
(defmacro debug->> [& fns]
  (reset! debug [])
  `(->> ~@(interleave fns (repeat 'debug*))))

=> (debug->> (map inc [1 2 3 4 5])
             (filter odd?))

(3 5)

=> @debug

[{:fn (map inc [1 2 3 4 5]), :ret (2 3 4 5 6)}
 {:fn (filter odd? (debug* (map inc [1 2 3 4 5]))), :ret (3 5)}
 {:fn (map inc [1 2 3 4 5]), :ret (2 3 4 5 6)}]
```

There are two issues with the output of our macro. The first is that it wrote three steps to the `debug` atom and there should only be two. The second is that we are only interested in the function for the second step `(filter odd?)`, not the whole chain of functions up to that point `(filter odd? (debug* (map inc [1 2 3 4 5])))`.

### Multiple evaluation and variable capture

Let's try and fix the first issue. The `clojure.walk/macroexpand-all` function recursively performs all possible macroexpansions in the form we give it. This can be really useful for working out what's going wrong with a macro.

```clojure
=> (clojure.walk/macroexpand-all
     '(debug->> (map inc [1 2 3 4 5])
             (filter odd?)))

(do
 (clojure.core/tap>
  (clojure.core/sorted-map
   :fn
   '(filter
     odd?
     (do
      (clojure.core/tap>
       (clojure.core/sorted-map
        :fn
        '(map inc [1 2 3 4 5])
        :ret
        (map inc [1 2 3 4 5])))
      (map inc [1 2 3 4 5])))
   :ret
   (filter
    odd?
    (do
     (clojure.core/tap>
      (clojure.core/sorted-map
       :fn
       '(map inc [1 2 3 4 5])
       :ret
       (map inc [1 2 3 4 5])))
     (map inc [1 2 3 4 5])))))
 (filter
  odd?
  (do
   (clojure.core/tap>
    (clojure.core/sorted-map
     :fn
     '(map inc [1 2 3 4 5])
     :ret
     (map inc [1 2 3 4 5])))
   (map inc [1 2 3 4 5]))))
```

Looking at the output code we can see that `tap>` appears four times. It gets evaluated three times, and it gets uses as data once. This is consistent with our output, which wrote to our `debug` atom three times and one of the results contained the `tap>` function that had not been evaluated and stored in `:fn`. Multiple evaluation is one of the pitfalls of macros writing to watch out for.

The reason the tap function is getting evaluated so many times is because our code calls `~args` several times. Once to be passed into our `:ret` value to get the result, once as a return value of the macro to be passed onto the next function and once to be passed into `quote`. We don't have to worry about this last value as `quote` will prevent it from being evaluated. However, the other two we only
want to evaluate once. We can do this by using a `let` binding and assigning `~args` to an auto-gensym value `args#` and then using that value in the rest of the macro.  Clojure automatically ensures that each instance of `args#` resolves to the same symbol within the same syntax-quoted list, this helps prevent another common pitfall of macro writing called variable capture. Variable capture is when a macro introduces a binding that shadows another binding unexpectedly leading to unexpected results.

```clojure
(defmacro debug* [args]
  `(let [args# ~args]
    (tap> (sorted-map :fn
                       (quote ~args)
                       :ret
                       args#))
     args#))
```

We can see what this expands to with `clojure.walk/macroexpand-all`.

```clojure
=> (clojure.walk/macroexpand-all
     '(debug->> (map inc [1 2 3 4 5])
             (filter odd?)))

(let*
 [args__1780__auto__
  (filter
   odd?
   (let*
    [args__1780__auto__ (map inc [1 2 3 4 5])]
    (clojure.core/tap>
     (clojure.core/sorted-map
      :fn
      '(map inc [1 2 3 4 5])
      :ret
      args__1780__auto__))
    args__1780__auto__))]
 (clojure.core/tap>
  (clojure.core/sorted-map
   :fn
   '(filter
     odd?
     (let*
      [args__1780__auto__ (map inc [1 2 3 4 5])]
      (clojure.core/tap>
       (clojure.core/sorted-map
        :fn
        '(map inc [1 2 3 4 5])
        :ret
        args__1780__auto__))
      args__1780__auto__))
   :ret
   args__1780__auto__))
 args__1780__auto__)

```

Everything looks right. There are only two calls to tap that will get evaluated.

```clojure
(defmacro debug->> [& fns]
  (reset! debug [])
  `(->> ~@(interleave fns (repeat 'debug*))))

=> (debug->> (map inc [1 2 3 4 5])
             (filter odd?))

(3 5)

=> @debug

[{:fn (map inc [1 2 3 4 5]), :ret (2 3 4 5 6)}
 {:fn (filter odd? (debug* (map inc [1 2 3 4 5]))), :ret (3 5)}]
```

Excellent.

### Removing debug* from the output

Now let's see if we can fix the second issue and only show `(filter odd?)` for the second step, not `(filter odd? (debug* (map inc [1 2 3 4 5])))`. To do this we create a function `drop-debug` that removes any nested lists that start with `'debug*`.

```clojure
(reset! debug [])

(defn drop-debug [sexp]
  (if (list? sexp)
    (remove #(and (list? %) (-> % first (= 'debug*))) sexp)
    sexp))

(defmacro debug* [args]
  `(let [args# ~args]
     (tap> (sorted-map :fn
                       (-> (quote ~args) drop-debug)
                       :ret
                       args#))
     args#))

(defmacro debug->> [& fns]
  (reset! debug [])
  `(->> ~@(interleave fns (repeat 'debug*))))

=> (debug->> (map inc [1 2 3 4 5])
             (filter odd?))

(3 5)

=> @debug

[{:fn (map inc [1 2 3 4 5]), :ret (2 3 4 5 6)}
 {:fn (filter odd?), :ret (3 5)}]
```

Perfect.

### Writing the debug-> macro

Finally lets implement `debug->` for good measure.

```clojure
(defmacro debug-> [& fns]
  (reset! debug [])
  `(-> ~@(interleave fns (repeat 'debug*))))

=> (debug-> (assoc {} :a 1)
            (update :a inc))

{:a 2}

=> @debug

[{:fn (assoc {} :a 1), :ret {:a 1}}
 {:fn (update :a inc), :ret {:a 2}}]
```

This concludes our tutorial on how to make a debug macro with Clojure 1.10's tap system.
