title:  Clojure: pretty print strings

`clojure.pprint/pprint` can be used to print Clojure data structures in a pleasing, easy-to-understand format. However, sometimes you are not printing directly to `*out*`, for example when using third party logging libraries/services. Thankfully, we can also use `clojure.pprint/pprint` generate a pretty string by wrapping it in `with-out-str`.

```clojure
(with-out-str
  (clojure.pprint/pprint
   {:some {:nested "data"}
    :that "really shouldn't"
    :be   "printed on a single line"}))

=>
"{:some {:nested \"data\"},\n :that \"really shouldn't\",\n :be \"printed on a single line\"}\n"
```

`with-out-str` evaluates an expression in a context in which `*out*` is bound to a fresh `StringWriter` and returns a string.

This string can then be passed into the logging function and will preserve the pretty formatting when printed.

```clojure
(defn log [message]
  (println message))

(-> (with-out-str
      (clojure.pprint/pprint
       {:some {:nested "data"}
        :that "really shouldn't"
        :be   "printed on a single line"}))
    log)

=>
{:some {:nested "data"},
 :that "really shouldn't",
 :be   "printed on a single line"}
```
