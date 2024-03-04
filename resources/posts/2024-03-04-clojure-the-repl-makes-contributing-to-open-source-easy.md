Title: Clojure: the REPL makes contributing to open source easy

Clojure has a great interactive development experience. This makes it surprisingly easy to contribute to open source. It goes something like this: you're using an open source library, you run into a potential bug, you clone the library, you write some code, you evaluate it and you see if the bug is fixed (all without ever having to restart your REPL).

Recently, I've been using a fantastic datalog database library called [Datalevin](https://github.com/juji-io/datalevin). I ran into a small issue (now fixed) where the option for validating data for more useful error messages `:validate-data?` didn't seem to be working as intended.

Instead of:

```clojure
(let [sc {:user/name
          {:db/valueType :db.type/string}}
      es [{:db/id -1 :user/name 34}]
      db (d/empty-db "foo" sc
           {:validate-data? true})]
  (d/db-with db es)
  (d/close-db db))

=>
Execution error (ExceptionInfo) 
at datalevin.storage/insert-data (storage.cljc:616).
Invalid data, expecting:db.type/long got "foo"
```

We get:

```clojure
(let [sc {:user/name
          {:db/valueType :db.type/string}}
      es [{:db/id -1 :user/name 34}]
      db (d/empty-db "foo" sc
           {:validate-data? true})]
  (d/db-with db es)
  (d/close-db db))

=>
Execution error (ClassCastException) 
at datalevin.bits/string-bytes (bits.cljc:441).
class java.lang.Long cannot be cast to class 
java.lang.String (java.lang.Long and java.lang.String 
are in module java.base of loader 'bootstrap')
```

Let's get the stack trace:

```clojure
(-> *e Throwable->map :trace)


=>
[[datalevin.bits$long_header invokeStatic "bits.cljc" 513]
 [datalevin.bits$long_header invoke "bits.cljc" 506]
 [datalevin.bits$val_header invokeStatic "bits.cljc" 745]
 [datalevin.bits$val_header invoke "bits.cljc" 730]
 [datalevin.bits$indexable invokeStatic "bits.cljc" 805]
 [datalevin.bits$indexable invoke "bits.cljc" 801]
 [datalevin.storage$insert_data invokeStatic "storage.cljc" 614]
 [datalevin.storage$insert_data invoke "storage.cljc" 604]
 ...
```

Now clone the `datalevin` repository and checkout the tag for the version of the library being used.

Search the source for `:validate-data?` or `validate-data?` (catches uses of destructuring).

```clojure
src/datalevin/storage.cljc
616: (or (not (:validate-data? (opts store)) ...
```

Jump to the code:

```clojure
(defn- insert-data
  [^Store store ^Datom d ft-ds giants]
  (let [attr  (.-a d)
        _     (or (not (:closed-schema?
                        (opts store)))
                ((schema store) attr)
                (u/raise
                  "Attribute not defined in schema "
                  attr {}))
        props (or ((schema store) attr)
                (swap-attr store attr identity))
        vt    (value-type props)
        ref?  (= :db.type/ref vt)
        e     (.-e d)
        v     (.-v d)
        aid   (:db/aid props)
A       i     (b/indexable e aid v vt)
        ft?   (:db/fulltext props)]
B   (or (not (:validate-data?
              (opts store)))
      (b/valid-data? v vt)
      (u/raise "Invalid data, expecting "
        vt {:input v}))
        
...
```

Looking at the code above for `insert-data` and the previous stack trace we can determine that the code on line **B** where `(:validate-data? ...)` is never reached because the stack trace shows `datalevin.bits$indexable` the code `(b/indexable ...)` on line **A** being called (meaning the error was thrown somewhere in `(b/indexable ...)`).

In this case the fix is simple. We move the code on line **B** to be called before the code on line **A**. That way if the data is not valid we'll get the helpful error message.

```clojure
(defn- insert-data
  [^Store store ^Datom d ft-ds giants]
  (let [attr  (.-a d)
        _     (or (not (:closed-schema?
                        (opts store)))
                ((schema store) attr)
                (u/raise
                  "Attribute not defined in schema "
                  attr {}))
        props (or ((schema store) attr)
                (swap-attr store attr identity))
        vt    (value-type props)
        ref?  (= :db.type/ref vt)
        e     (.-e d)
        v     (.-v d)
        aid   (:db/aid props)
        _     (or (not (:validate-data?
                        (opts store)))
                (b/valid-data? v vt)
                (u/raise "Invalid data, expecting "
                  vt {:input v}))
        i     (b/indexable e aid v vt)
        ft?   (:db/fulltext props)]

...
```

After the changes are made. Load the names space ( `(in-ns 'datalevin.storage)`) , evaluate `insert-data` and test the changes by evaluating the code that caused the error in your application.

```clojure
(let [sc {:user/name
          {:db/valueType :db.type/string}}
      es [{:db/id -1 :user/name 34}]
      db (d/empty-db "foo" sc
           {:validate-data? true})]
  (d/db-with db es)
  (d/close-db db))

=>
Execution error (ExceptionInfo) 
at datalevin.storage/insert-data (storage.cljc:616).
Invalid data, expecting :db.type/long
```

We are now getting the expected error message.

What's great about the REPL is it makes debugging the issue and testing the fix so easy. At no point have I had to build the third party project. Technically you don't even have to download the repository. However, it can be useful for searching the code, and you'll have to do it for making a pull request, running tests etc.

This reduction in friction makes contributing fixes to open source less of a time investment. For me personally, that difference means I'm more likely to open a pull request with a potential fix than create an issue asking for something to be fixed.

