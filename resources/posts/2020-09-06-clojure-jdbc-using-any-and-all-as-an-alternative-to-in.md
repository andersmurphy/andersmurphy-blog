Title: Clojure: jdbc using any and all as alternatives for in

[next-jdbc](https://github.com/seancorfield/next-jdbc) uses parameterised queries to prevent SQL Injections. These queries can take parameters by passing question marks (?) in the query and then by replacing each question mark index with required values. However this can make some sql operators more challenging to use programmatically. In particular `in(?,?,?)`. In this post we cover using [postgresql](https://www.postgresql.org/)'s `any(?)` and `all(?)` to get around this.

First we need to set up a database:

```clojure
(ns in-any-all.core
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [clojure.string :as str]
            [next.jdbc.prepare :as p])
  (:import [java.sql PreparedStatement]))

(def db {:dbtype "postgresql" :dbname "databasename"})
(def ds (jdbc/get-datasource db))
```

We need a table:

```clojure
(jdbc/execute!
  ds
  ["create table user_info (pid serial primary key, name text not null)"])
 (jdbc/execute! ds ["create unique index user_info_unique ON user_info(name)"])
```

And some data:

```clojure
(sql/insert! ds :user_info {:name "Bob"})
(sql/insert! ds :user_info {:name "Jane"})
(sql/insert! ds :user_info {:name "Megan"})
(sql/insert! ds :user_info {:name "Alice"})
```

Use `in` to find users with the name Bob or Jane:

```clojure
(sql/query ds ["select * from user_info where name in(?, ?)" "Bob" "Jane"])

=>
[#:user_info{:pid 1, :name "Bob"} #:user_info{:pid 2, :name "Jane"}]

```

Use `not in` to find users who don't have the name Bob or Jane:

```clojure
(sql/query ds ["select * from user_info where name not in(?, ?)" "Bob" "Jane"])

=>
[#:user_info{:pid 3, :name "Megan"} #:user_info{:pid 4, :name "Alice"}]

```

If we want to use parameterised queries with a variable number of names we would need to do something like this:

```clojure
(sql/query ds
            (let [names ["Bob" "Jane"]]
              (into [(str "select * from user_info where name in ("
                          (str/join ", " (repeat (count names) "?"))
                          ")")]
                    names)))

=>
[#:user_info{:pid 1, :name "Bob"} #:user_info{:pid 2, :name "Jane"}]

```

The above is quite cumbersome. If we rewrite our query to use `=` and `any` we can pass an array to the parameterised query instead:

```clojure
(sql/query ds
            ["select * from user_info where name = any(?)"
             (into-array String ["Bob" "Jane"])])

=>
[#:user_info{:pid 1, :name "Bob"} #:user_info{:pid 2, :name "Jane"}]
```

If we want the same behaviour as `not in` we can use `!=` and `all`:

```clojure
(sql/query ds
            ["select * from user_info where name != all(?)"
             (into-array String ["Bob" "Jane"])])

=>
[#:user_info{:pid 3, :name "Megan"} #:user_info{:pid 4, :name "Alice"}]
```

Though this approach isn't without inconveniences as we have to specify the array type and use `into-array`.

We can streamline this by extending the `next.jdbc.prepare/SettableParameter` protocol to automatically convert Clojure vectors to the appropriate typed array:

```clojure
(extend-protocol p/SettableParameter
 clojure.lang.IPersistentVector
   (set-parameter [v ^PreparedStatement s i]
     (let [conn      (.getConnection s)
           meta      (.getParameterMetaData s)
           type-name (.getParameterTypeName meta i)]
       (if-let [elem-type (when (= (first type-name) \_)
                            (apply str (rest type-name)))]
         (.setObject s i (.createArrayOf conn elem-type (to-array v)))
         (.setObject i s v)))))
```

So now we can pass vectors straight into our parameterised queries:

```clojure
(sql/query ds ["select * from user_info where name = any(?)" ["Bob" "Jane"]])

=>
[#:user_info{:pid 1, :name "Bob"} #:user_info{:pid 2, :name "Jane"}]

(sql/query ds ["select * from user_info where name != all(?)" ["Bob" "Jane"]])

=>
[#:user_info{:pid 3, :name "Megan"} #:user_info{:pid 4, :name "Alice"}]
```

This also supports different homogeneous array types automatically:

```clojure
(sql/query ds ["select * from user_info where pid != all(?)" [1 2]])

=>
[#:user_info{:pid 3, :name "Megan"} #:user_info{:pid 4, :name "Alice"}]

(sql/query ds ["select * from user_info where pid = any(?)" [1 2]])

=>
[#:user_info{:pid 1, :name "Bob"} #:user_info{:pid 2, :name "Jane"}]
```

The full example project can be found
[here](https://github.com/andersmurphy/clj-cookbook/tree/master/sql/in-any-all).
