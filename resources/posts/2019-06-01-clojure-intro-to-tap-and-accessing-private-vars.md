Title: Clojure: intro to tap> and accessing private vars

Clojure 1.10 introduced a new system called tap. From the release notes: *tap is a shared, globally accessible system for distributing a series of informational or diagnostic values to a set of (presumably effectful) handler functions. It can be used as a better debug prn, or for facilities like logging etc.*

Tap has a nice simple api. We can send a value to the set of registered handler functions with `tap>`. We can register a handlers function with `add-tap`. Finally we can unregister a handler function with `remove-tap`.

### Adding a tap handler and sending values

We create an atom `bar` and register an anonymous handler function to the tap system with `add-tap`. This will `conj` any values we pass to `tap>` to the `bar` atom.

```clojure
(def bar (atom []))

(add-tap (partial swap! bar conj))

(tap> (inc 1))

=> @bar

[2]

(tap> "foo")

=> @bar

[2 "foo"]
```

When we dereference `bar` we get the values `[2 "foo"]` that have been passed to `tap>`. What happens if we add the same anonymous handler function to the tap system again.

```clojure
(reset! bar [])

(add-tap (partial swap! bar conj))

(tap> "foo")

=> @bar

["foo" "foo"]
```

Surprising, even though we called tap once two `"foo"`s got written to our atom. Let's investigate the `add-tap` source to see if we can work out what's going on.

```clojure
=> (source add-tap)

(defn add-tap
  [f]
  (force tap-loop)
  (swap! tapset conj f)
  nil)
```

So `add-tap` adds the tap handlers to an atom called `tapset`. From the name, we can guess that it might be a set which means we shouldn't be able to register the same tap function twice. Let's try and access `tapset`.

```clojure
=> clojure.core/tapset

Syntax error compiling at (form-init1817879857542651664.clj:1:1).
var: clojure.core/tapset is not public
```

No luck, `tapset` is not public.

### Creating and accessing private vars

In Clojure you can create private vars by adding the key `:private` to a var's metadata.

```clojure
=> (def ^:private private-var "foo")

#'user/private-var

(ns baz)

=> user/private-var

Syntax error compiling at (form-init1817879857542651664.clj:1:1).
var: user/private-var is not public
```

Even though these private vars are not intended to be accessed we can still work around this by using `#'` to refer directly to the var. We can then dereference it to access its value.

```clojure
=> #'user/private-var

#'user/private-var

=> @#'user/private-var

"foo"
```

There are rarely any reasons to ever have to do this in production code, and even then it would not be advisable. However, it can be very useful when exploring a new api in the repl.

### Back to tapset

Armed with our new knowledge of how to access private vars we can find out what's in `tapset`. Notice the `@@` we need to derefence `tapset` twice: once to get the value of the var, and once to get the value of the atom.

```clojure
=> @@#'clojure.core/tapset

#{#object[clojure.core$partial$fn__5831 0x18852ca3 "clojure.core$partial$fn__5831@18852ca3"]
  #object[clojure.core$partial$fn__5831 0xb50d66f "clojure.core$partial$fn__5831@b50d66f"]}
```

It looks like our anonymous functions are not unique and therefore count as different functions as far as tap is concerned. Let's `reset!` the `tapset`.

```clojure
(reset! bar [])
(reset! @#'clojure.core/tapset #{})

(tap> "foo")

=> @bar

[]
```

Back to normal. So if we want to be able to prevent the same function from getting added multiple times, we need to give it a name.

```clojure
(defn conj-to-bar [x]
  (swap! bar conj x))

(add-tap conj-to-bar)
(add-tap conj-to-bar)

=> @@#'clojure.core/tapset

#{#object[user$conj_to_bar 0x4f1e0067 "user$conj_to_bar@4f1e0067"]}
```

Even though we called the `add-tap` function twice with the same function, it only got added once.

### Removing tap handlers

The other advantage of using named functions is that you can use `remove-tap` to remove tap functions from the `tapset`. With an anonymous function you would have to hang on to the a reference to be able to remove it from the `tapset`.

```clojure
(remove-tap conj-to-bar)

=> @@#'clojure.core/tapset

#{}
```

This concludes our initial intro to Clojure 1.10's tap system and some useful tricks for accessing private vars.
