Title: Clojure: ensuring multimethods are required

Multimethods are fantastic. They give you polymorphism without objects or classes (the best part of Object Oriented without the baggage), multiple dispatch, dynamic dispatch and strong decoupling that allows you to extend code without modifying it ([open closed principle](https://en.wikipedia.org/wiki/Open%E2%80%93closed_principle)), this even extends to third party code. This decoupling is so good that it's not unheard of to deploy your system without all the `defmethod` extensions being required! This post will teach you how to prevent this.

The `printer` namespace defines the print multimethod which dispatches on the `:type` of it's input.

```Clojure
(ns ensuring-multimethods-are-required.printer
  (:refer-clojure :exclude [print]))

(defmulti print :type)

(defmethod print :default [{:keys [text]}] (println text))
```

A namespace, called `shout`, defines a `p/print` method that upper cases `:text` before printing.

```Clojure
(ns ensuring-multimethods-are-required.shout
  (:require [ensuring-multimethods-are-required.printer :as p]
            [clojure.string :as str]))

(defmethod p/print :shout [{:keys [text]}]
  (println (str/upper-case text)))
```

Another namespace, called `whisper`, defines a `p/print` method that lower cases `:text` before printing.

```Clojure
(ns ensuring-multimethods-are-required.whisper
  (:require [ensuring-multimethods-are-required.printer :as p]
            [clojure.string :as str]))

(defmethod p/print :whisper [{:keys [text]}]
  (println (str/lower-case text)))
```

In the `core` namespace the `p/print` multimethod is called with a series of data.

At the top of the file (after the namespace declaration) an assertion checks that the `p/print` multimethod has the expected multimethods implementation registered to it.

```Clojure
(ns ensuring-multimethods-are-required.core
  (:require
   [ensuring-multimethods-are-required.printer :as p]))

(let [loaded-methods (-> p/print methods keys set)
      expected-methods #{:default :shout :whisper}]
  (assert (= expected-methods loaded-methods)
          (str expected-methods " =/= " loaded-methods)))

(run! p/print [{:text "Hello"}
               {:type :shout :text "Hello"}
               {:type :whisper :text "Hello"}])
```

In this case two of the desired implementations are missing and an error is thrown when trying to compile the namespace.

```Clojure
Syntax error (AssertionError) compiling at (ensuring_multimethods_are_required/core.clj:5:1).
Assert failed: #{:shout :default :whisper} =/= #{:default}
(= expected-methods loaded-methods)
```

The missing `shout` and `whisper` namespaces are added to the namespace deceleration.

```Clojure
(ns ensuring-multimethods-are-required.core
  (:require
   [ensuring-multimethods-are-required.whisper]
   [ensuring-multimethods-are-required.shout]
   [ensuring-multimethods-are-required.printer :as p]))

(let [loaded-methods (-> p/print methods keys set)
      expected-methods #{:default :shout :whisper}]
  (assert (= expected-methods loaded-methods)
          (str expected-methods " =/= " loaded-methods)))

(run! p/print [{:text "Hello"}
               {:type :shout :text "Hello"}
               {:type :whisper :text "Hello"}])

```

Everything now works as expected.

```Clojure
Hello
HELLO
hello

nil
```

This trick helps avoid unexpected behaviour caused by missing multimethod implementations.

The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/multimethods/ensuring-multimethods-are-required).
