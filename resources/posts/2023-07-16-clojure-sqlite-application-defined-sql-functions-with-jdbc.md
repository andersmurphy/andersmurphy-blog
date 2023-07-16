Title: Clojure: SQLite application defined SQL functions with JDBC

SQLite provides a convenient interface called [Application-Defined SQL Functions](https://www.sqlite.org/appfunc.html) that lets you create custom SQL functions that call back into your application code to compute their results. This lets us 
extend SQLite in Clojure. Want to query with regex or jsonpath? Want custom aggregation functions? Don't want to write C? No problem Application Defined SQL Functions have you covered. 

## In Java

 [org.sqlite](https://github.com/xerial/sqlite-jdbc) provides an abstract class `org.sqlite.Function` for implementing application defined  SQL functions.

Bellow is how `org.sqlite.Function` is intended to be used from java using an anonymous class that extends `org.sqlite.Function`.

```
Function.create(conn, "hello_world", new Function() {
 @Override
 protected void xFunc() {
   result("Hello, world!")
 }
}, 0, Function.FLAG_DETERMINISTIC);
```

## In Clojure

Clojure provides the `proxy` function for creating anonymous classes. However, it doesn't allow you to access protected super methods, and unfortunately `org.sqlite.Function`  implements a bunch of methods as protected.

### Create a gen-class

`gen-class` creates named classes for direct use as Java classes and allows us to expose inherited protected methods.

```Clojure
(gen-class
  :name sqlite.db.application-defined-functions.RegexCapture
  :prefix "regex-capture-"
  :extends org.sqlite.Function
  :exposes-methods {result superResult
                    value_text superValueText})
```

We extend `org.sqlite.Function` and expose the methods `result` and `value_text` binding them to `superResult` and `superValueText` respectively.

### Override xFunc

We use `defn` to override the `org.sqlite.Function` class's `xFunc` method. It's important to note that the function name prefix must match the prefix specified in `gen-class` under `:prefix`. In this case that's `regex-capture-`.

```Clojure
(defn regex-capture-xFunc [this]
  (.superResult this
    (let [result (re-find
                   (re-pattern
                     (.superValueText  this 0))
                   (.superValueText  this 1))]
      (if (vector? result)
        (second result)
        result))))
```

### Create a directory called classes

Ensure the default target output directory `classes` exists at the top level of the project.

### Add classes directory to paths

Add the classes directory to the class path in `deps.edn`.

```Clojure
{:paths   ["src" "classes"]
 :deps    {org.clojure/clojure               {:mvn/version "1.11.1"}
           com.github.seancorfield/next.jdbc {:mvn/version "1.3.874"}
           org.xerial/sqlite-jdbc            {:mvn/version "3.42.0.0"}}
 :aliases {}}
```

### Generate the classes

Compile to generate classes using the `compile` function.

```
(compile 'sqlite.db.application-defined-functions)
```

### Tests the SQL functions

To use the application defined SQL functions we need to create them. This loads them into SQLite for the duration of the current connection, meaning they can be used in any query that uses the same connection.

```Clojure
(let [my-datasource (jdbc/get-datasource
                      {:jdbcUrl "jdbc:sqlite:db/database.db"})]
  (with-open [conn (jdbc/get-connection my-datasource)]
    (Function/create
      conn
      "regex_capture"
      (sqlite.db.application-defined-functions.RegexCapture.))
    (jdbc/execute! conn
      ["select regex_capture(?, 'Hello, world!')"
       ", (world)!"])))

=>
[{:regex_capture(?, 'Hello, world!') "world"}]
```

Magic! SQLite is executing functions defined in Clojure.

### Bonus: Automatically compile the SQL functions

To compile our SQLite function on repl launch add the following `:main-opts` to
the `:dev` alias.

```
{:paths   ["src" "classes"]
 :deps
 {org.clojure/clojure               {:mvn/version "1.11.1"}
  com.github.seancorfield/next.jdbc {:mvn/version "1.3.874"}
  org.xerial/sqlite-jdbc            {:mvn/version "3.42.0.0"}}
 :aliases
 {:dev
  {:main-opts
   [;; Ensures application defined functions are compiled
    ;; As they use gen-class to extend org.sqlite.Function
    "-e" "(compile 'sqlite.db.application-defined-functions)"
    "-r"]}}}
```

This means we don't have to remember to manually compile the SQLite functions.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/sqlite/application-defined-sql-functions).

Next post I'll be sharing a helper macro that makes writing Application Defined SQL functions more convenient.
